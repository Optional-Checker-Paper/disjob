/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.scheduler.supervisor.thread;

import cn.ponfee.scheduler.common.base.exception.CheckedThrowing;
import cn.ponfee.scheduler.common.date.Dates;
import cn.ponfee.scheduler.common.lock.DoInLocked;
import cn.ponfee.scheduler.core.base.AbstractHeartbeatThread;
import cn.ponfee.scheduler.core.enums.*;
import cn.ponfee.scheduler.core.exception.JobException;
import cn.ponfee.scheduler.core.model.SchedJob;
import cn.ponfee.scheduler.core.model.SchedTask;
import cn.ponfee.scheduler.core.model.SchedInstance;
import cn.ponfee.scheduler.supervisor.manager.SchedulerJobManager;
import cn.ponfee.scheduler.supervisor.util.TriggerTimeUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DuplicateKeyException;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static cn.ponfee.scheduler.core.base.JobConstants.PROCESS_BATCH_SIZE;

/**
 * The schedule job heartbeat thread, <br/>
 * find the sched_job which will be trigger, <br/>
 * split to one sched_instance and many sched_task
 *
 * @author Ponfee
 */
public class TriggeringJobScanner extends AbstractHeartbeatThread {

    private static final int SCAN_COLLISION_INTERVAL_SECONDS = 60;

    private final DoInLocked doInLocked;
    private final SchedulerJobManager schedulerJobManager;
    private final long afterMilliseconds;

    public TriggeringJobScanner(long heartbeatPeriodMilliseconds,
                                DoInLocked doInLocked,
                                SchedulerJobManager schedulerJobManager) {
        super(heartbeatPeriodMilliseconds);
        this.doInLocked = doInLocked;
        this.schedulerJobManager = schedulerJobManager;
        this.afterMilliseconds = (heartbeatPeriodMs * 3); // 3s * 3 = 9s
    }

    @Override
    protected boolean heartbeat() {
        if (schedulerJobManager.hasNotDiscoveredWorkers()) {
            log.warn("Not found available worker.");
            return true;
        }
        Boolean result = doInLocked.apply(() -> {
            Date now = new Date();
            long maxNextTriggerTime = now.getTime() + afterMilliseconds;
            List<SchedJob> jobs = schedulerJobManager.findBeTriggering(maxNextTriggerTime, PROCESS_BATCH_SIZE);
            if (jobs == null || jobs.isEmpty()) {
                return true;
            }
            for (SchedJob job : jobs) {
                processJob(job, now, maxNextTriggerTime);
            }
            return false;
        });

        return result == null || result;
    }

    private void processJob(SchedJob job, Date now, long maxNextTriggerTime) {
        try {
            // 重新再计算一次nextTriggerTime
            job.setNextTriggerTime(recomputeNextTriggerTime(job, now));
            if (job.getNextTriggerTime() == null) {
                String reason = "Stop recompute reason: has not next trigger time";
                job.setRemark(reason);
                log.info("{} | {}", reason, job);
                schedulerJobManager.stopJob(job);
                return;
            } else if (job.getNextTriggerTime() > maxNextTriggerTime) {
                schedulerJobManager.updateNextTriggerTime(job);
                return;
            }

            // check has available workers
            if (schedulerJobManager.hasNotDiscoveredWorkers(job.getJobGroup())) {
                updateNextScanTime(job, now, 15);
                log.warn("Scan job not found available group '{}' workers.", job.getJobGroup());
                return;
            }

            // check collision with last schedule
            if (checkBlockCollisionTrigger(job, now)) {
                return;
            }

            // 1、build sched instance and sched task list
            SchedInstance instance = SchedInstance.create(
                schedulerJobManager.generateId(), job.getJobId(), RunType.SCHEDULE, job.getNextTriggerTime(), 0, now
            );
            List<SchedTask> tasks = schedulerJobManager.splitTasks(job, instance.getInstanceId(), now);

            // 2、refresh next trigger time
            refreshNextTriggerTime(job, job.getNextTriggerTime(), now);

            // 3、update and save data
            if (schedulerJobManager.updateAndSave(job, instance, tasks)) {
                // 4、dispatch job task
                schedulerJobManager.dispatch(job, instance, tasks);
            }
        } catch (DuplicateKeyException e){
            if (schedulerJobManager.updateNextTriggerTime(job)) {
                log.info("Conflict trigger time: {} | {}", job, e.getMessage());
            } else {
                log.error("Conflict trigger time: {} | {}", job, e.getMessage());
            }
        } catch (JobException | IllegalArgumentException e) {
            log.error(e.getMessage() + ": " + job, e);
            job.setRemark("Stop reason: " + e.getMessage());
            job.setNextTriggerTime(null);
            schedulerJobManager.stopJob(job);
        } catch (Exception e) {
            log.error("Process handle job occur error: " + job, e);
        }
    }

    /**
     * Recompute the job next trigger time.
     *
     * @param job the job
     * @param now the current date
     * @return accurate next trigger time milliseconds
     */
    private Long recomputeNextTriggerTime(SchedJob job, Date now) {
        if (now.getTime() <= (job.getNextTriggerTime() + afterMilliseconds)) {
            // 1、如果没有过期：保持原有的nextTriggerTime
            return job.getNextTriggerTime();
        } else {
            // 2、其它情况：基于原来的lastTriggerTime重新再计算一次nextTriggerTime
            return TriggerTimeUtils.computeNextTriggerTime(job, now);
        }
    }

    /**
     * Check is whether block if the next trigger collision
     *
     * @param job the sched job
     * @param now the now date time
     * @return {@code true} will block the next trigger
     */
    private boolean checkBlockCollisionTrigger(SchedJob job, Date now) {
        CollisionStrategy collisionStrategy = CollisionStrategy.of(job.getCollisionStrategy());
        Long lastTriggerTime;
        if (CollisionStrategy.CONCURRENT == collisionStrategy || (lastTriggerTime = job.getLastTriggerTime()) == null) {
            return false;
        }

        SchedInstance lastInstance = schedulerJobManager.getByTriggerTime(job.getJobId(), lastTriggerTime, RunType.SCHEDULE.value());
        if (lastInstance == null) {
            return false;
        }

        long instanceId = lastInstance.getInstanceId();
        RunState runState = RunState.of(lastInstance.getRunState());
        switch (runState) {
            case FINISHED:
                return false;
            case WAITING:
            case PAUSED:
                return checkBlockCollisionTrigger(job, Collections.singletonList(instanceId), collisionStrategy, now);
            case RUNNING:
                List<SchedTask> tasks = schedulerJobManager.findMediumTaskByInstanceId(instanceId);
                if (schedulerJobManager.hasAliveExecuting(tasks)) {
                    return checkBlockCollisionTrigger(job, Collections.singletonList(instanceId), collisionStrategy, now);
                } else {
                    // all workers are dead
                    log.info("Collision, all worker dead, terminate the sched instance: {}", instanceId);
                    schedulerJobManager.cancelInstance(instanceId, Operations.COLLISION_CANCEL);
                    return false;
                }
            case CANCELED:
                List<SchedInstance> list = schedulerJobManager.findUnterminatedRetry(instanceId);
                if (CollectionUtils.isEmpty(list)) {
                    return false;
                } else {
                    List<Long> instanceIds = list.stream().map(SchedInstance::getInstanceId).collect(Collectors.toList());
                    return checkBlockCollisionTrigger(job, instanceIds, collisionStrategy, now);
                }
            default:
                throw new UnsupportedOperationException("Unsupported run state: " + runState.name());
        }
    }

    private boolean checkBlockCollisionTrigger(SchedJob job, List<Long> instanceIds, CollisionStrategy collisionStrategy, Date now) {
        switch (collisionStrategy) {
            case DISCARD:
                // 丢弃执行：基于当前时间来更新下一次的执行时间
                Integer misfireStrategy = job.getMisfireStrategy();
                try {
                    job.setMisfireStrategy(MisfireStrategy.DISCARD.value());
                    job.setNextTriggerTime(TriggerTimeUtils.computeNextTriggerTime(job, now));
                } finally {
                    // restore
                    job.setMisfireStrategy(misfireStrategy);
                }
                if (job.getNextTriggerTime() == null) {
                    // It has not next triggered time, then stop the job
                    job.setRemark("Disable collision reason: has not next trigger time.");
                    job.setJobState(JobState.DISABLE.value());
                }
                schedulerJobManager.updateNextTriggerTime(job);
                return true;
            case SERIAL:
                // 串行执行：更新下一次的扫描时间
                updateNextScanTime(job, now, SCAN_COLLISION_INTERVAL_SECONDS);
                return true;
            case OVERRIDE:
                // 覆盖执行：先取消上一次的执行
                instanceIds.forEach(instanceId -> CheckedThrowing.supplier(() -> schedulerJobManager.cancelInstance(instanceId, Operations.COLLISION_CANCEL)));
                return false;
            default:
                throw new UnsupportedOperationException("Unsupported collision strategy: " + collisionStrategy.name());
        }
    }

    private void updateNextScanTime(SchedJob job, Date now, int delayedSeconds) {
        Date nextScanTime = Dates.plusSeconds(now, delayedSeconds);
        schedulerJobManager.updateNextScanTime(job.getJobId(), nextScanTime, job.getVersion());
    }

    /**
     * Refresh the job next trigger time.
     *
     * @param job             the job
     * @param lastTriggerTime the lastTriggerTime
     * @param now             the current date
     */
    private static void refreshNextTriggerTime(SchedJob job, Long lastTriggerTime, Date now) {
        job.setLastTriggerTime(lastTriggerTime);
        job.setNextTriggerTime(TriggerTimeUtils.computeNextTriggerTime(job, now));
        if (job.getNextTriggerTime() == null) {
            // It has not next triggered time, then stop the job
            job.setRemark("Disable refresh reason: has not next trigger time");
            job.setJobState(JobState.DISABLE.value());
        }
    }

}
