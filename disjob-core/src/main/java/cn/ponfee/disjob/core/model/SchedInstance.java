/* __________              _____                                                *\
** \______   \____   _____/ ____\____   ____    Copyright (c) 2017-2023 Ponfee  **
**  |     ___/  _ \ /    \   __\/ __ \_/ __ \   http://www.ponfee.cn            **
**  |    |  (  <_> )   |  \  | \  ___/\  ___/   Apache License Version 2.0      **
**  |____|   \____/|___|  /__|  \___  >\___  >  http://www.apache.org/licenses/ **
**                      \/          \/     \/                                   **
\*                                                                              */

package cn.ponfee.disjob.core.model;

import cn.ponfee.disjob.common.model.BaseEntity;
import cn.ponfee.disjob.common.util.Jsons;
import cn.ponfee.disjob.core.enums.RunState;
import cn.ponfee.disjob.core.enums.RunType;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.beans.Transient;
import java.io.Serializable;
import java.util.Date;

/**
 * The schedule instance entity, mapped database table sched_instance
 *
 * @author Ponfee
 */
@Getter
@Setter
public class SchedInstance extends BaseEntity implements Serializable {

    private static final long serialVersionUID = -1457861792948169949L;

    /**
     * 全局唯一ID
     */
    private Long instanceId;

    /**
     * root instance_id(Retry、Depend、Workflow)
     */
    private Long rnstanceId;

    /**
     * parent instance_id(Retry、Depend、Workflow)
     */
    private Long pnstanceId;

    /**
     * sched_job.job_type为Workflow生成的lead instance_id
     */
    private Long wnstanceId;

    /**
     * sched_job.job_id
     *
     * @see SchedJob#getJobId()
     */
    private Long jobId;

    /**
     * 触发时间(毫秒时间戳)
     */
    private Long triggerTime;

    /**
     * 运行类型：1-SCHEDULE；2-DEPEND；3-RETRY；4-MANUAL(手动触发)；
     *
     * @see RunType
     */
    private Integer runType;

    /**
     * 运行状态：10-待运行；20-运行中；30-已暂停；40-已完成；50-已取消；
     *
     * @see RunState
     */
    private Integer runState;

    /**
     * 运行开始时间
     */
    private Date runStartTime;

    /**
     * 运行结束时间
     */
    private Date runEndTime;

    /**
     * 运行时长(毫秒)
     */
    private Long runDuration;

    /**
     * 已重试的次数(the maximum value is sched_job.retry_count)
     */
    private Integer retriedCount;

    /**
     * 附加信息
     *
     * @see InstanceAttach
     */
    private String attach;

    /**
     * 行记录版本号
     */
    private Integer version;

    /**
     * Creates sched instance
     *
     * @param instanceId   the instance id
     * @param jobId        the job id
     * @param runType      the run type
     * @param triggerTime  the trigger time
     * @param retriedCount the retried count
     * @param date         the creates date
     * @return SchedInstance
     */
    public static SchedInstance create(long instanceId, long jobId, RunType runType,
                                       long triggerTime, int retriedCount, Date date) {
        SchedInstance instance = new SchedInstance();
        instance.setInstanceId(instanceId);
        instance.setJobId(jobId);
        instance.setRunType(runType.value());
        instance.setTriggerTime(triggerTime);
        instance.setRetriedCount(retriedCount);
        instance.setUpdatedAt(date);
        instance.setCreatedAt(date);
        instance.setRunState(RunState.WAITING.value());
        return instance;
    }

    public long obtainLockInstanceId() {
        return wnstanceId != null ? wnstanceId : instanceId;
    }

    /**
     * Obtain root instance id
     *
     * @return root instance id
     */
    public Long obtainRnstanceId() {
        if (rnstanceId != null) {
            return rnstanceId;
        }
        if (pnstanceId != null) {
            return pnstanceId;
        }
        return instanceId;
    }

    @Transient
    public boolean isWorkflow() {
        return wnstanceId != null;
    }

    @Transient
    public boolean isWorkflowNode() {
        return isWorkflow() && !wnstanceId.equals(instanceId);
    }

    @Transient
    public boolean isWorkflowLead() {
        return isWorkflow() && wnstanceId.equals(instanceId);
    }

    public InstanceAttach parseAttach() {
        if (StringUtils.isBlank(attach)) {
            return null;
        }
        return Jsons.fromJson(attach, InstanceAttach.class);
    }

}
