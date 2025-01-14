/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.supervisor.service;

import cn.ponfee.disjob.common.base.IdGenerator;
import cn.ponfee.disjob.common.base.LazyLoader;
import cn.ponfee.disjob.common.collect.Collects;
import cn.ponfee.disjob.common.dag.DAGEdge;
import cn.ponfee.disjob.common.dag.DAGNode;
import cn.ponfee.disjob.common.spring.TransactionUtils;
import cn.ponfee.disjob.common.tuple.Tuple2;
import cn.ponfee.disjob.common.tuple.Tuple3;
import cn.ponfee.disjob.common.util.Functions;
import cn.ponfee.disjob.common.util.Jsons;
import cn.ponfee.disjob.common.util.Strings;
import cn.ponfee.disjob.core.base.Worker;
import cn.ponfee.disjob.core.dag.WorkflowGraph;
import cn.ponfee.disjob.core.enums.*;
import cn.ponfee.disjob.core.exception.JobCheckedException;
import cn.ponfee.disjob.core.model.*;
import cn.ponfee.disjob.core.param.*;
import cn.ponfee.disjob.dispatch.TaskDispatcher;
import cn.ponfee.disjob.registry.SupervisorRegistry;
import cn.ponfee.disjob.supervisor.base.WorkerCoreRpcClient;
import cn.ponfee.disjob.supervisor.dao.mapper.*;
import cn.ponfee.disjob.supervisor.instance.NormalInstanceCreator;
import cn.ponfee.disjob.supervisor.instance.TriggerInstance;
import cn.ponfee.disjob.supervisor.instance.TriggerInstanceCreator;
import cn.ponfee.disjob.supervisor.instance.WorkflowInstanceCreator;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.ponfee.disjob.core.base.JobConstants.PROCESS_BATCH_SIZE;
import static cn.ponfee.disjob.supervisor.base.AbstractDataSourceConfig.TX_TEMPLATE_NAME_SUFFIX;
import static cn.ponfee.disjob.supervisor.dao.SupervisorDataSourceConfig.DB_NAME;

/**
 * Manage distributed schedule job.
 *
 * @author Ponfee
 */
@Component
public class DistributedJobManager extends AbstractJobManager {
    private final static Logger LOG = LoggerFactory.getLogger(DistributedJobManager.class);

    private static final Interner<Long> INTERNER_POOL = Interners.newWeakInterner();

    private static final List<Integer> RUN_STATE_TERMINABLE = Collects.convert(RunState.TERMINABLE_LIST, RunState::value);
    private static final List<Integer> RUN_STATE_RUNNABLE = Collects.convert(RunState.RUNNABLE_LIST, RunState::value);
    private static final List<Integer> RUN_STATE_PAUSABLE = Collects.convert(RunState.PAUSABLE_LIST, RunState::value);
    private static final List<Integer> RUN_STATE_WAITING = Collections.singletonList(RunState.WAITING.value());
    private static final List<Integer> RUN_STATE_RUNNING = Collections.singletonList(RunState.RUNNING.value());
    private static final List<Integer> RUN_STATE_PAUSED = Collections.singletonList(RunState.PAUSED.value());

    private static final List<Integer> EXECUTE_STATE_EXECUTABLE = Collects.convert(ExecuteState.EXECUTABLE_LIST, ExecuteState::value);
    private static final List<Integer> EXECUTE_STATE_PAUSABLE = Collects.convert(ExecuteState.PAUSABLE_LIST, ExecuteState::value);
    private static final List<Integer> EXECUTE_STATE_WAITING = Collections.singletonList(ExecuteState.WAITING.value());
    private static final List<Integer> EXECUTE_STATE_PAUSED = Collections.singletonList(ExecuteState.PAUSED.value());

    private final TransactionTemplate transactionTemplate;
    private final SchedInstanceMapper instanceMapper;
    private final SchedTaskMapper taskMapper;
    private final SchedWorkflowMapper workflowMapper;

    public DistributedJobManager(SchedJobMapper jobMapper,
                                 SchedDependMapper dependMapper,
                                 SchedInstanceMapper instanceMapper,
                                 SchedTaskMapper taskMapper,
                                 SchedWorkflowMapper workflowMapper,
                                 IdGenerator idGenerator,
                                 SupervisorRegistry discoveryWorker,
                                 TaskDispatcher taskDispatcher,
                                 WorkerCoreRpcClient workerCoreRpcClient,
                                 @Qualifier(DB_NAME + TX_TEMPLATE_NAME_SUFFIX) TransactionTemplate transactionTemplate) {
        super(jobMapper, dependMapper, idGenerator, discoveryWorker, taskDispatcher, workerCoreRpcClient);
        this.transactionTemplate = transactionTemplate;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.workflowMapper = workflowMapper;
    }

    // ------------------------------------------------------------------database single operation without spring transactional

    public boolean renewInstanceUpdateTime(SchedInstance instance, Date updateTime) {
        return instanceMapper.renewUpdateTime(instance.getInstanceId(), updateTime, instance.getVersion()) == AFFECTED_ONE_ROW;
    }

    @Override
    protected boolean cancelWaitingTask(long taskId) {
        return taskMapper.terminate(taskId, null, ExecuteState.BROADCAST_ABORTED.value(), ExecuteState.WAITING.value(), null, null) == AFFECTED_ONE_ROW;
    }

    public void savepoint(long taskId, String executeSnapshot) {
        Assert.state(taskMapper.savepoint(taskId, executeSnapshot) == AFFECTED_ONE_ROW, () -> "Save point failed: " + taskId + " | " + executeSnapshot);
    }

    // ------------------------------------------------------------------database operation within spring transactional

    /**
     * Manual trigger the sched job
     *
     * @param jobId the job id
     * @throws JobCheckedException if occur error
     */
    @Transactional(transactionManager = TX_MANAGER_NAME, rollbackFor = Exception.class)
    public void triggerJob(long jobId) throws JobCheckedException {
        SchedJob job = jobMapper.get(jobId);
        Assert.notNull(job, () -> "Sched job not found: " + jobId);

        TriggerInstanceCreator creator = TriggerInstanceCreator.of(job.getJobType(), this);
        TriggerInstance tInstance = creator.create(job, RunType.MANUAL, System.currentTimeMillis());
        createInstance(tInstance);
        TransactionUtils.doAfterTransactionCommit(() -> creator.dispatch(job, tInstance));
    }

    /**
     * Update sched job, save sched instance and tasks.
     *
     * @param job             the job
     * @param triggerInstance the trigger instance
     * @return {@code true} if operated success
     */
    @Transactional(transactionManager = TX_MANAGER_NAME, rollbackFor = Exception.class)
    public boolean createInstance(SchedJob job, TriggerInstance triggerInstance) {
        if (jobMapper.updateNextTriggerTime(job) == 0) {
            // operation conflicted
            return false;
        }
        createInstance(triggerInstance);
        return true;
    }

    /**
     * Set or clear task worker
     *
     * @param params the list of update task worker params
     */
    @Transactional(transactionManager = TX_MANAGER_NAME, rollbackFor = Exception.class)
    public void updateTaskWorker(List<TaskWorkerParam> params) {
        if (CollectionUtils.isNotEmpty(params)) {
            // Sort for prevent sql deadlock: Deadlock found when trying to get lock; try restarting transaction
            params.sort(Comparator.comparing(TaskWorkerParam::getTaskId));
            Collects.batchProcess(params, taskMapper::batchUpdateWorker, PROCESS_BATCH_SIZE);
        }
    }

    /**
     * Starts the task
     *
     * @param param the start task param
     * @return {@code true} if start successfully
     */
    @Transactional(transactionManager = TX_MANAGER_NAME, rollbackFor = Exception.class)
    public boolean startTask(StartTaskParam param) {
        SchedInstance instance = instanceMapper.get(param.getInstanceId());
        Assert.notNull(instance, () -> "Sched instance not found: " + param);
        // sched_instance.run_state must in (WAITING, RUNNING)
        if (!RUN_STATE_PAUSABLE.contains(instance.getRunState())) {
            return false;
        }

        Date now = new Date();
        // start sched instance(also possibly started by other task)
        int row = 0;
        if (RunState.WAITING.equals(instance.getRunState())) {
            row = instanceMapper.start(param.getInstanceId(), now);
        }

        // start sched task
        if (taskMapper.start(param.getTaskId(), param.getWorker(), now) == 0) {
            Assert.state(row == 0, () -> "Start task failed: " + param);
            return false;
        } else {
            return true;
        }
    }

    public void changeInstanceState(long instanceId, ExecuteState toExecuteState) {
        RunState toRunState = toExecuteState.runState();
        Assert.isTrue(toExecuteState != ExecuteState.EXECUTING, "Cannot force update state to EXECUTING");
        doTransactionLockInSynchronized(instanceId, null, instance -> {
            Assert.notNull(instance, () -> "Sched instance not found: " + instanceId);
            Assert.isTrue(!instance.isWorkflow(), () -> "Unsupported force change workflow instance state: " + instanceId);

            int instRow = instanceMapper.changeState(instanceId, toRunState.value());
            int taskRow = taskMapper.changeState(instanceId, toExecuteState.value());
            if (instRow == 0 && taskRow == 0) {
                throw new IllegalStateException("Force update instance state failed: " + instanceId);
            }

            if (toExecuteState == ExecuteState.WAITING) {
                Tuple3<SchedJob, SchedInstance, List<SchedTask>> params = buildDispatchParams(instanceId, taskRow);
                TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(params.a, params.b, params.c));
            }

            LOG.info("Force change state success {} | {}", instanceId, toExecuteState);
        });
    }

    public void deleteInstance(long instanceId) {
        Long wnstanceId = instanceMapper.getWnstanceId(instanceId);
        doTransactionLockInSynchronized(instanceId, wnstanceId, instance -> {
            Assert.notNull(instance, () -> "Sched instance not found: " + instanceId);
            Assert.isTrue(RunState.of(instance.getRunState()).isTerminal(), () -> "Deleting instance must be terminal: " + instance);

            if (instance.isWorkflow()) {
                Assert.isTrue(instance.isWorkflowLead(), () -> "Cannot delete workflow node instance: " + instanceId);

                // delete workflow lead instance
                int row = instanceMapper.deleteByInstanceId(instanceId);
                Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Delete workflow lead instance conflict: " + instanceId);

                // delete task
                for (SchedInstance e : instanceMapper.findWorkflowNode(instance.getWnstanceId())) {
                    row = taskMapper.deleteByInstanceId(e.getInstanceId());
                    Assert.isTrue(row >= AFFECTED_ONE_ROW, () -> "Delete sched task conflict: " + instanceId);
                }

                // delete workflow node instance
                row = instanceMapper.deleteByWnstanceId(instanceId);
                Assert.isTrue(row >= AFFECTED_ONE_ROW, () -> "Delete workflow node instance conflict: " + instanceId);

                // delete workflow config
                row = workflowMapper.deleteByWnstanceId(instanceId);
                Assert.isTrue(row >= AFFECTED_ONE_ROW, () -> "Delete sched workflow conflict: " + instanceId);
            } else {
                int row = instanceMapper.deleteByInstanceId(instanceId);
                Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Delete sched instance conflict: " + instanceId);

                row = taskMapper.deleteByInstanceId(instanceId);
                Assert.isTrue(row >= AFFECTED_ONE_ROW, () -> "Delete sched task conflict: " + instanceId);
            }
            LOG.info("Delete sched instance success {}", instanceId);
        });
    }

    // ------------------------------------------------------------------terminate task & instance

    /**
     * Terminate task
     *
     * @param param the terminal task param
     * @return {@code true} if terminated task successful
     */
    public boolean terminateTask(TerminateTaskParam param) {
        Assert.hasText(param.getWorker(), "Terminate task worker cannot be blank.");
        ExecuteState toState = param.getToState();
        long instanceId = param.getInstanceId();
        Assert.isTrue(!ExecuteState.PAUSABLE_LIST.contains(toState), () -> "Stop executing invalid to state " + toState);
        return doTransactionLockInSynchronized(instanceId, param.getWnstanceId(), instance -> {
            Assert.notNull(instance, () -> "Terminate executing task failed, instance not found: " + instanceId);
            Assert.isTrue(!instance.isWorkflowLead(), () -> "Cannot direct terminate workflow lead instance: " + instance);
            if (RunState.of(instance.getRunState()).isTerminal()) {
                // already terminated
                return false;
            }

            Date executeEndTime = toState.isTerminal() ? new Date() : null;
            int row = taskMapper.terminate(param.getTaskId(), param.getWorker(), toState.value(), ExecuteState.EXECUTING.value(), executeEndTime, param.getErrorMsg());
            if (row != AFFECTED_ONE_ROW) {
                // usual is worker invoke http timeout, then retry
                LOG.warn("Conflict terminate executing task: {} | {}", param.getTaskId(), toState);
                return false;
            }

            Tuple2<RunState, Date> tuple = obtainRunState(taskMapper.findBaseByInstanceId(instanceId));
            if (tuple != null && instanceMapper.terminate(instanceId, tuple.a.value(), RUN_STATE_TERMINABLE, tuple.b) > 0) {
                // the last executing task of this sched instance
                if (param.getOperation().isTrigger()) {
                    instance.setRunState(tuple.a.value());
                    afterTerminateTask(instance);
                } else if (instance.isWorkflowNode()) {
                    updateWorkflowEdgeState(instance, tuple.a.value(), RUN_STATE_TERMINABLE);
                    updateWorkflowLeadState(instanceMapper.get(param.getWnstanceId()));
                }
            }

            return true;
        });
    }

    /**
     * Purge the zombie instance which maybe dead
     *
     * @param inst the sched instance
     * @return {@code true} if purged successfully
     */
    public boolean purgeInstance(SchedInstance inst) {
        Long instanceId = inst.getInstanceId();
        return doTransactionLockInSynchronized(instanceId, inst.getWnstanceId(), instance -> {
            Assert.notNull(instance, () -> "Purge instance not found: " + instanceId);
            Assert.isTrue(!instance.isWorkflowLead(), () -> "Cannot purge workflow lead instance: " + instance);
            // instance run state must in (10, 20)
            if (!RUN_STATE_PAUSABLE.contains(instance.getRunState())) {
                return false;
            }

            // task execute state must not 10
            List<SchedTask> tasks = taskMapper.findBaseByInstanceId(instanceId);
            if (tasks.stream().anyMatch(e -> ExecuteState.WAITING.equals(e.getExecuteState()))) {
                LOG.warn("Purge instance failed, has waiting task: {}", tasks);
                return false;
            }

            // if task execute state is 20, cannot is alive
            if (hasAliveExecuting(tasks)) {
                LOG.warn("Purge instance failed, has alive executing task: {}", tasks);
                return false;
            }

            Tuple2<RunState, Date> tuple = obtainRunState(tasks);
            if (tuple == null) {
                tuple = Tuple2.of(RunState.CANCELED, new Date());
            } else {
                // cannot be paused
                Assert.isTrue(tuple.a.isTerminal(), () -> "Purge instance state must be terminal state: " + instance);
            }
            if (instanceMapper.terminate(instanceId, tuple.a.value(), RUN_STATE_TERMINABLE, tuple.b) != AFFECTED_ONE_ROW) {
                return false;
            }

            tasks.stream()
                .filter(e -> EXECUTE_STATE_PAUSABLE.contains(e.getExecuteState()))
                .forEach(e -> {
                    String worker = ExecuteState.EXECUTING.equals(e.getExecuteState()) ? Strings.requireNonBlank(e.getWorker()) : null;
                    taskMapper.terminate(e.getTaskId(), worker, ExecuteState.EXECUTE_TIMEOUT.value(), e.getExecuteState(), new Date(), null);
                });

            instance.setRunState(tuple.a.value());
            afterTerminateTask(instance);

            LOG.warn("Purge instance {} to state {}", instanceId, tuple.a);
            return true;
        });
    }

    /**
     * Pause instance
     *
     * @param instanceId the instance id, if workflow then lead instance id
     * @return {@code true} if paused successfully
     */
    public boolean pauseInstance(long instanceId) {
        Long wnstanceId = instanceMapper.getWnstanceId(instanceId);
        if (wnstanceId != null) {
            Assert.isTrue(instanceId == wnstanceId, () -> "Must pause lead workflow instance: " + instanceId);
        }
        return doTransactionLockInSynchronized(instanceId, wnstanceId, instance -> {
            Assert.notNull(instance, () -> "Pause instance not found: " + instanceId);
            if (!RUN_STATE_PAUSABLE.contains(instance.getRunState())) {
                return false;
            }

            if (instance.isWorkflow()) {
                Assert.isTrue(instance.isWorkflowLead(), () -> "Cannot pause workflow node instance: " + instanceId);
                // update sched_workflow waiting node to paused state
                workflowMapper.update(instanceId, null, RunState.PAUSED.value(), null, RUN_STATE_WAITING, null);
                // pause sched_workflow running node
                instanceMapper.findWorkflowNode(instanceId)
                    .stream()
                    .filter(e -> RUN_STATE_PAUSABLE.contains(e.getRunState()))
                    .forEach(this::pauseInstance);
                // update sched_workflow running lead
                updateWorkflowLeadState(instance);
            } else {
                pauseInstance(instance);
            }

            return true;
        });
    }

    /**
     * Cancel instance
     *
     * @param instanceId the sched_instance.instance_id, if workflow then is sched_instance.wnstance_id
     * @param ops        the operation
     * @return {@code true} if canceled successfully
     */
    public boolean cancelInstance(long instanceId, Operations ops) {
        Assert.isTrue(ops.toState().isFailure(), () -> "Cancel instance operation invalid: " + ops);
        Long wnstanceId = instanceMapper.getWnstanceId(instanceId);
        if (wnstanceId != null) {
            Assert.isTrue(instanceId == wnstanceId, () -> "Must pause lead workflow instance: " + instanceId);
        }
        return doTransactionLockInSynchronized(instanceId, wnstanceId, instance -> {
            Assert.notNull(instance, () -> "Cancel instance not found: " + instanceId);
            if (RunState.of(instance.getRunState()).isTerminal()) {
                return false;
            }

            if (instance.isWorkflow()) {
                Assert.isTrue(instance.isWorkflowLead(), () -> "Cannot cancel workflow node instance: " + instanceId);
                workflowMapper.update(instanceId, null, RunState.CANCELED.value(), null, RUN_STATE_WAITING, null);
                instanceMapper.findWorkflowNode(instanceId)
                    .stream()
                    .filter(e -> !RunState.of(e.getRunState()).isTerminal())
                    .forEach(e -> cancelInstance(e, ops));
                updateWorkflowLeadState(instance);
            } else {
                cancelInstance(instance, ops);
            }

            return true;
        });
    }

    /**
     * Resume the instance from paused to waiting state
     *
     * @param instanceId the instance id
     * @return {@code true} if resumed successfully
     */
    public boolean resumeInstance(long instanceId) {
        Long wnstanceId = instanceMapper.getWnstanceId(instanceId);
        return doTransactionLockInSynchronized(instanceId, wnstanceId, instance -> {
            Assert.notNull(instance, () -> "Cancel failed, instance_id not found: " + instanceId);
            if (!RunState.PAUSED.equals(instance.getRunState())) {
                return false;
            }

            if (instance.isWorkflow()) {
                Assert.isTrue(instance.isWorkflowLead(), () -> "Cannot resume workflow node instance: " + instanceId);
                // update sched_instance paused lead to running state
                int row = instanceMapper.updateState(instanceId, RunState.RUNNING.value(), RunState.PAUSED.value());
                Assert.state(row == AFFECTED_ONE_ROW, () -> "Resume workflow lead instance failed: " + instanceId);
                workflowMapper.resumeWaiting(instanceId);
                for (SchedInstance nodeInstance : instanceMapper.findWorkflowNode(instanceId)) {
                    if (RunState.PAUSED.equals(nodeInstance.getRunState())) {
                        resumeInstance(nodeInstance);
                        updateWorkflowEdgeState(nodeInstance, RunState.RUNNING.value(), RUN_STATE_PAUSED);
                    }
                }
                WorkflowGraph graph = new WorkflowGraph(workflowMapper.findByWnstanceId(wnstanceId));
                createWorkflowNode(instance, graph, graph.map(), ExceptionUtils::rethrow);
            } else {
                resumeInstance(instance);
            }

            return true;
        });
    }

    // ------------------------------------------------------------------private methods

    private void doTransactionLockInSynchronized(long instanceId, Long wnstanceId, Consumer<SchedInstance> action) {
        doTransactionLockInSynchronized(instanceId, wnstanceId, Functions.convert(action, Boolean.TRUE));
    }

    /**
     * 加JVM锁是为了避免单节点内对数据库锁的等待及数据连接超时
     *
     * @param instanceId the instance id
     * @param wnstanceId the workflow instance id
     * @param action the action
     * @return boolean value of action result
     */
    private boolean doTransactionLockInSynchronized(long instanceId, Long wnstanceId, Function<SchedInstance, Boolean> action) {
        // Long.toString(lockKey).intern()
        Long lockInstanceId = wnstanceId == null ? instanceId : wnstanceId;
        synchronized (INTERNER_POOL.intern(lockInstanceId)) {
            Boolean result = transactionTemplate.execute(status -> {
                SchedInstance lockedInstance = instanceMapper.lock(lockInstanceId);
                Assert.notNull(lockedInstance, () -> "Lock instance not found: " + lockInstanceId);
                SchedInstance instance = (instanceId == lockInstanceId) ? lockedInstance : instanceMapper.get(instanceId);
                Assert.notNull(instance, () -> "Instance not found: " + instance);
                if (!Objects.equals(instance.getWnstanceId(), wnstanceId)) {
                    throw new IllegalArgumentException("Invalid workflow instance id: " + wnstanceId + ", " + instance);
                }
                return action.apply(instance);
            });
            return Boolean.TRUE.equals(result);
        }
    }

    private Tuple2<RunState, Date> obtainRunState(List<SchedTask> tasks) {
        List<ExecuteState> states = tasks.stream().map(SchedTask::getExecuteState).map(ExecuteState::of).collect(Collectors.toList());
        if (states.stream().allMatch(ExecuteState::isTerminal)) {
            // executeEndTime is null: canceled task maybe never not started
            return Tuple2.of(
                states.stream().anyMatch(ExecuteState::isFailure) ? RunState.CANCELED : RunState.FINISHED,
                tasks.stream().map(SchedTask::getExecuteEndTime).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElseGet(Date::new)
            );
        }
        // if task has WAITING or EXECUTING state, then return null
        return states.stream().anyMatch(ExecuteState.PAUSABLE_LIST::contains) ? null : Tuple2.of(RunState.PAUSED, null);
    }

    private void createInstance(TriggerInstance tInstance) {
        instanceMapper.insert(tInstance.getInstance());

        if (tInstance instanceof NormalInstanceCreator.NormalInstance) {
            NormalInstanceCreator.NormalInstance creator = (NormalInstanceCreator.NormalInstance) tInstance;
            Collects.batchProcess(creator.getTasks(), taskMapper::batchInsert, PROCESS_BATCH_SIZE);
        } else if (tInstance instanceof WorkflowInstanceCreator.WorkflowInstance) {
            WorkflowInstanceCreator.WorkflowInstance creator = (WorkflowInstanceCreator.WorkflowInstance) tInstance;
            Collects.batchProcess(creator.getWorkflows(), workflowMapper::batchInsert, PROCESS_BATCH_SIZE);
            for (Tuple2<SchedInstance, List<SchedTask>> sub : creator.getNodeInstances()) {
                instanceMapper.insert(sub.a);
                Collects.batchProcess(sub.b, taskMapper::batchInsert, PROCESS_BATCH_SIZE);
            }
        } else {
            throw new UnsupportedOperationException("Unknown instance creator type: " + tInstance.getClass());
        }
    }

    private void pauseInstance(SchedInstance instance) {
        Assert.isTrue(RUN_STATE_PAUSABLE.contains(instance.getRunState()), () -> "Invalid pause instance state: " + instance);
        long instanceId = instance.getInstanceId();
        Operations ops = Operations.PAUSE;

        // 1、update task state: (WAITING) -> (PAUSE)
        taskMapper.updateStateByInstanceId(instanceId, ops.toState().value(), EXECUTE_STATE_WAITING, null);

        // 2、load the alive executing tasks
        List<ExecuteTaskParam> executingTasks = loadExecutingTasks(instance, ops);
        if (executingTasks.isEmpty()) {
            // has non executing task, update sched instance state
            Tuple2<RunState, Date> tuple = obtainRunState(taskMapper.findBaseByInstanceId(instanceId));
            // must be paused or terminate
            Assert.notNull(tuple, () -> "Pause instance failed: " + instanceId);
            int row = instanceMapper.terminate(instanceId, tuple.a.value(), RUN_STATE_PAUSABLE, tuple.b);
            Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Pause instance failed: " + instance + " | " + tuple.a);
            if (instance.isWorkflowNode()) {
                updateWorkflowEdgeState(instance, tuple.a.value(), RUN_STATE_PAUSABLE);
            }
        } else {
            // has alive executing tasks: dispatch and pause executing tasks
            TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(executingTasks));
        }
    }

    private void cancelInstance(SchedInstance instance, Operations ops) {
        long instanceId = instance.getInstanceId();
        // 1、update: (WAITING or PAUSED) -> (CANCELED)
        taskMapper.updateStateByInstanceId(instanceId, ops.toState().value(), EXECUTE_STATE_EXECUTABLE, new Date());

        // 2、load the alive executing tasks
        List<ExecuteTaskParam> executingTasks = loadExecutingTasks(instance, ops);
        if (executingTasks.isEmpty()) {
            // has non executing execute_state
            Tuple2<RunState, Date> tuple = obtainRunState(taskMapper.findBaseByInstanceId(instanceId));
            Assert.notNull(tuple, () -> "Cancel instance failed: " + instanceId);
            // if all task paused, should update to canceled state
            if (tuple.a == RunState.PAUSED) {
                tuple = Tuple2.of(RunState.CANCELED, new Date());
            }

            RunState toState = tuple.a;
            int row = instanceMapper.terminate(instanceId, toState.value(), RUN_STATE_TERMINABLE, tuple.b);
            Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Cancel instance failed: " + instance + " | " + toState);
            if (instance.isWorkflowNode()) {
                updateWorkflowEdgeState(instance, tuple.a.value(), RUN_STATE_TERMINABLE);
            }
        } else {
            // dispatch and cancel executing tasks
            TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(executingTasks));
        }
    }

    private void resumeInstance(SchedInstance instance) {
        long instanceId = instance.getInstanceId();
        int row = instanceMapper.updateState(instanceId, RunState.WAITING.value(), RunState.PAUSED.value());
        Assert.state(row == AFFECTED_ONE_ROW, "Resume sched instance failed.");

        row = taskMapper.updateStateByInstanceId(instanceId, ExecuteState.WAITING.value(), EXECUTE_STATE_PAUSED, null);
        Assert.state(row >= AFFECTED_ONE_ROW, "Resume sched task failed.");

        // dispatch task
        Tuple3<SchedJob, SchedInstance, List<SchedTask>> params = buildDispatchParams(instanceId, row);
        TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(params.a, params.b, params.c));
    }

    private void updateWorkflowLeadState(SchedInstance instance) {
        Assert.isTrue(instance.isWorkflowLead(), () -> "Must terminate workflow lead instance: " + instance);
        long wnstanceId = instance.getWnstanceId();
        List<SchedWorkflow> workflows = workflowMapper.findByWnstanceId(wnstanceId);

        WorkflowGraph graph = new WorkflowGraph(workflows);
        updateWorkflowEndState(graph);

        if (graph.allMatch(e -> e.getValue().isTerminal())) {
            RunState state = graph.anyMatch(e -> e.getValue().isFailure()) ? RunState.CANCELED : RunState.FINISHED;
            int row = instanceMapper.terminate(instance.getWnstanceId(), state.value(), RUN_STATE_TERMINABLE, new Date());
            Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Update workflow lead instance state failed: " + instance + " | " + state);
        } else if (workflows.stream().noneMatch(e -> RunState.RUNNING.equals(e.getRunState()))) {
            RunState state = RunState.PAUSED;
            int row = instanceMapper.updateState(instance.getWnstanceId(), state.value(), instance.getRunState());
            Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Update workflow lead instance state failed: " + instance + " | " + state);
        }
    }

    private void updateWorkflowEdgeState(SchedInstance instance, Integer toState, List<Integer> fromStates) {
        String curNode = instance.parseAttach().getCurNode();
        int row = workflowMapper.update(instance.getWnstanceId(), curNode, toState, null, fromStates, instance.getInstanceId());
        Assert.isTrue(row > 0, () -> "Update workflow state failed: " + instance + " | " + toState);
    }

    private void updateWorkflowEndState(WorkflowGraph graph) {
        long wnstanceId = Collects.getFirst(graph.map().values()).getWnstanceId();
        // if end node is not terminal state, then process the end node run state
        if (graph.anyMatch(e -> e.getKey().getTarget().isEnd() && !e.getValue().isTerminal())) {
            Map<DAGEdge, SchedWorkflow> ends = graph.predecessors(DAGNode.END);
            if (ends.values().stream().allMatch(SchedWorkflow::isTerminal)) {
                RunState endState = ends.values().stream().anyMatch(SchedWorkflow::isFailure) ? RunState.CANCELED : RunState.FINISHED;
                int row = workflowMapper.update(wnstanceId, DAGNode.END.toString(), endState.value(), null, RUN_STATE_TERMINABLE, null);
                Assert.isTrue(row > 0, () -> "Update workflow end node failed: " + wnstanceId + " | " + endState);
                ends.forEach((k, v) -> graph.get(k.getTarget(), DAGNode.END).setRunState(endState.value()));
            }
        }
    }

    private void createWorkflowNode(SchedInstance leadInstance, WorkflowGraph graph, Map<DAGEdge, SchedWorkflow> map, Function<Throwable, Boolean> failHandler) {
        SchedJob job = LazyLoader.of(SchedJob.class, jobMapper::get, leadInstance.getJobId());
        long wnstanceId = leadInstance.getWnstanceId();
        Date now = new Date();
        Set<DAGNode> duplicates = new HashSet<>();
        for (Map.Entry<DAGEdge, SchedWorkflow> entry : map.entrySet()) {
            DAGNode target = entry.getKey().getTarget();
            SchedWorkflow workflow = entry.getValue();
            if (target.isEnd() || !RunState.WAITING.equals(workflow.getRunState()) || !duplicates.add(target)) {
                // 当前节点为结束结点 或 当前节点不为等待状态，则跳过
                continue;
            }

            Map<DAGEdge, SchedWorkflow> predecessors = graph.predecessors(target);
            if (predecessors.values().stream().anyMatch(e -> !RunState.of(e.getRunState()).isTerminal())) {
                // 前置节点还未结束，则跳过
                continue;
            }

            if (predecessors.values().stream().anyMatch(e -> RunState.of(e.getRunState()).isFailure())) {
                RunState state = RunState.CANCELED;
                int row = workflowMapper.update(wnstanceId, workflow.getCurNode(), state.value(), null, RUN_STATE_TERMINABLE, null);
                Assert.isTrue(row > 0, () -> "Update workflow cur node state failed: " + workflow + " | " + state);
                continue;
            }

            try {
                long nextInstanceId = generateId();
                List<SchedTask> tasks = splitTasks(JobHandlerParam.from(job, target.getName()), nextInstanceId, new Date());
                long triggerTime = leadInstance.getTriggerTime() + workflow.getSequence();
                SchedInstance nextInstance = SchedInstance.create(nextInstanceId, job.getJobId(), RunType.of(leadInstance.getRunType()), triggerTime, 0, now);
                nextInstance.setRnstanceId(wnstanceId);
                nextInstance.setPnstanceId(predecessors.isEmpty() ? null : Collects.getFirst(predecessors.values()).getInstanceId());
                nextInstance.setWnstanceId(wnstanceId);
                nextInstance.setAttach(Jsons.toJson(new InstanceAttach(workflow.getCurNode())));

                int row = workflowMapper.update(wnstanceId, workflow.getCurNode(), RunState.RUNNING.value(), nextInstanceId, RUN_STATE_WAITING, null);
                Assert.isTrue(row > 0, () -> "Start workflow node failed: " + workflow);

                // save to db
                instanceMapper.insert(nextInstance);
                Collects.batchProcess(tasks, taskMapper::batchInsert, PROCESS_BATCH_SIZE);
                TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(job, nextInstance, tasks));
            } catch (Throwable t) {
                Boolean result = failHandler.apply(t);
                if (Boolean.FALSE.equals(result)) {
                    // if false then break
                    return;
                }
            }
        }
    }

    private void afterTerminateTask(SchedInstance instance) {
        RunState runState = RunState.of(instance.getRunState());
        if (runState == RunState.CANCELED) {
            retryJob(instance);
        } else if (runState == RunState.FINISHED) {
            if (instance.isWorkflowNode()) {
                processWorkflow(instance);
            } else {
                dependJob(instance);
            }
        } else {
            LOG.error("Unknown terminate run state " + runState);
        }
    }

    private void processWorkflow(SchedInstance nodeInstance) {
        if (!nodeInstance.isWorkflowNode()) {
            return;
        }

        RunState runState = RunState.of(nodeInstance.getRunState());
        Long wnstanceId = nodeInstance.getWnstanceId();

        updateWorkflowEdgeState(nodeInstance, runState.value(), RUN_STATE_TERMINABLE);

        if (runState == RunState.CANCELED) {
            workflowMapper.update(wnstanceId, null, RunState.CANCELED.value(), null, RUN_STATE_RUNNABLE, null);
        }

        WorkflowGraph graph = new WorkflowGraph(workflowMapper.findByWnstanceId(wnstanceId));
        updateWorkflowEndState(graph);

        // process workflows run state
        if (graph.allMatch(e -> e.getValue().isTerminal())) {
            RunState state = graph.anyMatch(e -> e.getValue().isFailure()) ? RunState.CANCELED : RunState.FINISHED;
            int row = instanceMapper.terminate(wnstanceId, state.value(), RUN_STATE_TERMINABLE, new Date());
            Assert.isTrue(row == AFFECTED_ONE_ROW, () -> "Terminate workflow lead instance failed: " + nodeInstance + " | " + state);
            afterTerminateTask(instanceMapper.get(wnstanceId));
            return;
        }

        if (runState == RunState.CANCELED) {
            return;
        }

        createWorkflowNode(
            instanceMapper.get(wnstanceId),
            graph,
            graph.successors(DAGNode.fromString(nodeInstance.parseAttach().getCurNode())),
            throwable -> {
                LOG.error("Split workflow job task error: " + nodeInstance, throwable);
                nodeInstance.setRunState(RunState.CANCELED.value());
                processWorkflow(nodeInstance);
                return false;
            }
        );
    }

    private void retryJob(SchedInstance prev) {
        SchedJob schedJob = jobMapper.get(prev.getJobId());
        if (schedJob == null) {
            LOG.error("Sched job not found {}", prev.getJobId());
            processWorkflow(prev);
            return;
        }

        List<SchedTask> prevTasks = taskMapper.findLargeByInstanceId(prev.getInstanceId());
        RetryType retryType = RetryType.of(schedJob.getRetryType());
        if (retryType == RetryType.NONE || schedJob.getRetryCount() < 1) {
            // not retry
            processWorkflow(prev);
            return;
        }

        int retriedCount = Optional.ofNullable(prev.getRetriedCount()).orElse(0);
        if (retriedCount >= schedJob.getRetryCount()) {
            // already retried maximum times
            processWorkflow(prev);
            return;
        }

        // 如果是workflow，则需要更新sched_workflow.instance_id
        long retryInstanceId = generateId();
        if (prev.isWorkflowNode()) {
            String curNode = prev.parseAttach().getCurNode();
            int row = workflowMapper.update(prev.getWnstanceId(), curNode, null, retryInstanceId, RUN_STATE_RUNNING, prev.getInstanceId());
            Assert.isTrue(row > 0, () -> "Retry workflow node failed: " + prev);
        }

        // 1、build sched instance
        retriedCount++;
        Date now = new Date();
        long triggerTime = schedJob.computeRetryTriggerTime(retriedCount, now);
        SchedInstance retryInstance = SchedInstance.create(retryInstanceId, schedJob.getJobId(), RunType.RETRY, triggerTime, retriedCount, now);
        retryInstance.setRnstanceId(prev.obtainRnstanceId());
        retryInstance.setPnstanceId(prev.getInstanceId());
        retryInstance.setWnstanceId(prev.getWnstanceId());
        retryInstance.setAttach(prev.getAttach());

        // 2、build sched tasks
        List<SchedTask> tasks;
        if (retryType == RetryType.ALL) {
            try {
                // re-split tasks
                tasks = splitTasks(JobHandlerParam.from(schedJob), retryInstance.getInstanceId(), now);
            } catch (Throwable t) {
                LOG.error("Split retry job error: " + schedJob + ", " + prev, t);
                processWorkflow(prev);
                return;
            }
        } else if (retryType == RetryType.FAILED) {
            tasks = prevTasks.stream()
                .filter(e -> ExecuteState.of(e.getExecuteState()).isFailure())
                // broadcast task cannot support partial retry
                .filter(e -> !RouteStrategy.BROADCAST.equals(schedJob.getRouteStrategy()) || super.isAliveWorker(e.getWorker()))
                .map(e -> SchedTask.create(e.getTaskParam(), generateId(), retryInstanceId, e.getTaskNo(), e.getTaskCount(), now, e.getWorker()))
                .collect(Collectors.toList());
        } else {
            LOG.error("Unknown job retry type {}", schedJob);
            processWorkflow(prev);
            return;
        }

        // 3、save to db
        Assert.notEmpty(tasks, "Insert list of task cannot be empty.");
        instanceMapper.insert(retryInstance);
        Collects.batchProcess(tasks, taskMapper::batchInsert, PROCESS_BATCH_SIZE);

        TransactionUtils.doAfterTransactionCommit(() -> super.dispatch(schedJob, retryInstance, tasks));
    }

    /**
     * Crates dependency job task.
     *
     * @param parentInstance the parent instance
     */
    private void dependJob(SchedInstance parentInstance) {
        List<SchedDepend> schedDepends = dependMapper.findByParentJobId(parentInstance.getJobId());
        if (CollectionUtils.isEmpty(schedDepends)) {
            return;
        }

        for (SchedDepend depend : schedDepends) {
            SchedJob childJob = jobMapper.get(depend.getChildJobId());
            if (childJob == null) {
                LOG.error("Child sched job not found: {} | {}", depend.getParentJobId(), depend.getChildJobId());
                continue;
            }
            if (JobState.DISABLE.equals(childJob.getJobState())) {
                continue;
            }

            Runnable dispatchAction = TransactionUtils.doInNestedTransaction(
                Objects.requireNonNull(transactionTemplate.getTransactionManager()),
                () -> {
                    TriggerInstanceCreator creator = TriggerInstanceCreator.of(childJob.getJobType(), this);
                    // ### Cause: java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '1003164910267351007-1683124620000-2' for key 'uk_jobid_triggertime_runtype'
                    // 加sequence解决唯一索引问题：UNIQUE KEY `uk_jobid_triggertime_runtype` (`job_id`, `trigger_time`, `run_type`)
                    //
                    // 极端情况还是会存在唯一索引值冲突：比如依赖的任务多于1000个，但这种情况可以限制所依赖父任务的个数来解决，暂不考虑
                    // parent1(trigger_time=1000, sequence=1001)，parent2(trigger_time=2000, sequence=1)
                    long triggerTime = (parentInstance.getTriggerTime() / 1000) * 1000 + depend.getSequence();
                    TriggerInstance tInstance = creator.create(childJob, RunType.DEPEND, triggerTime);
                    tInstance.getInstance().setRnstanceId(parentInstance.obtainRnstanceId());
                    tInstance.getInstance().setPnstanceId(parentInstance.getInstanceId());
                    createInstance(tInstance);
                    return () -> creator.dispatch(childJob, tInstance);
                },
                t -> LOG.error("Depend job instance created fail: " + parentInstance + " | " + childJob, t)
            );

            if (dispatchAction != null) {
                TransactionUtils.doAfterTransactionCommit(dispatchAction);
            }
        }
    }

    private List<ExecuteTaskParam> loadExecutingTasks(SchedInstance instance, Operations ops) {
        List<ExecuteTaskParam> executingTasks = new ArrayList<>();
        ExecuteTaskParamBuilder builder = ExecuteTaskParam.builder(instance, jobMapper::get);
        // immediate trigger
        long triggerTime = 0L;
        for (SchedTask task : taskMapper.findBaseByInstanceId(instance.getInstanceId())) {
            if (!ExecuteState.EXECUTING.equals(task.getExecuteState())) {
                continue;
            }
            Worker worker = Worker.deserialize(task.getWorker());
            if (super.isAliveWorker(worker)) {
                executingTasks.add(builder.build(ops, task.getTaskId(), triggerTime, worker));
            } else {
                // update dead task
                Date executeEndTime = ops.toState().isTerminal() ? new Date() : null;
                int row = taskMapper.terminate(task.getTaskId(), task.getWorker(), ops.toState().value(), ExecuteState.EXECUTING.value(), executeEndTime, null);
                if (row != AFFECTED_ONE_ROW) {
                    LOG.error("Cancel the dead task failed: {}", task);
                    executingTasks.add(builder.build(ops, task.getTaskId(), triggerTime, worker));
                } else {
                    LOG.info("Cancel the dead task success: {}", task);
                }
            }
        }
        return executingTasks;
    }

    private Tuple3<SchedJob, SchedInstance, List<SchedTask>> buildDispatchParams(long instanceId, int expectTaskSize) {
        SchedInstance instance = instanceMapper.get(instanceId);
        SchedJob job = jobMapper.get(instance.getJobId());
        List<SchedTask> waitingTasks = taskMapper.findLargeByInstanceId(instanceId)
            .stream()
            .filter(e -> ExecuteState.WAITING.equals(e.getExecuteState()))
            .collect(Collectors.toList());
        if (waitingTasks.size() != expectTaskSize) {
            throw new IllegalStateException("Invalid dispatching tasks size: expect=" + expectTaskSize + ", actual=" + waitingTasks.size());
        }
        return Tuple3.of(job, instance, waitingTasks);
    }

}
