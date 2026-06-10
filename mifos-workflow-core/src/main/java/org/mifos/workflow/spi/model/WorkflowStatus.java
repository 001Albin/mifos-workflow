package org.mifos.workflow.spi.model;

/**
 * Represents the lifecycle state of a workflow process instance.
 *
 * Used as a vendor-neutral status that both Flowable and Conductor
 * adapters map their engine-specific states into.
 *
 * Flowable mapping:
 *   active process instance    → RUNNING
 *   ended process instance     → COMPLETED
 *   deleted/terminated         → FAILED
 *   suspended                  → PAUSED
 *
 * Conductor mapping:
 *   RUNNING                    → RUNNING
 *   COMPLETED                  → COMPLETED
 *   FAILED / TIMED_OUT         → FAILED
 *   PAUSED                     → PAUSED
 */
public enum WorkflowStatus {

    /** Process is currently executing — steps are running or waiting at a human task */
    RUNNING,

    /** Process finished successfully — all steps completed without error */
    COMPLETED,

    /** Process ended abnormally — an error occurred or it was forcefully terminated */
    FAILED,

    /** Process is temporarily suspended — can be resumed */
    PAUSED
}