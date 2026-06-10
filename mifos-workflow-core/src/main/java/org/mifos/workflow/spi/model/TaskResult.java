package org.mifos.workflow.spi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents the result of a completed task within a workflow.
 *
 * Used by adapters to communicate task completion outcomes
 * back through the SPI layer.
 *
 * In Flowable: populated from TaskService completion response
 * In Conductor: populated from TaskResult after worker execution
 *
 * SECURITY: The outputData map may contain business data.
 * Adapters must not place credentials or raw passwords in this map.
 * Use variable names like "approvedBy" (a username) not "password".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {

    /**
     * The ID of the task that was completed.
     */
    private String taskId;

    /**
     * The ID of the process instance this task belongs to.
     */
    private String processInstanceId;

    /**
     * The name of the task that was completed.
     * e.g. "Manager Approval", "Document Verification"
     */
    private String taskName;

    /**
     * Outcome status of the task.
     * Maps to WorkflowStatus so it is engine-neutral.
     */
    private WorkflowStatus outcome;

    /**
     * The output variables produced by this task.
     * e.g. {"approved": true, "approvedBy": "john.doe", "note": "looks good"}
     *
     * SECURITY: Never store raw passwords or auth tokens here.
     */
    private Map<String, Object> outputData;

    /**
     * When the task was completed.
     */
    private LocalDateTime completedAt;

    /**
     * Who completed the task — username or system identifier.
     */
    private String completedBy;

    /**
     * Error message if the task failed — null if successful.
     */
    private String errorMessage;
}