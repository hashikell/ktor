package org.jetbrains.ktor.pipeline

import kotlinx.support.jdk7.*
import java.util.*

class PipelineMachine {
    private val executionStack = ArrayList<PipelineExecution>(4)

    fun <T : Any> execute(subject: T, pipeline: Pipeline<T>): Nothing {
        val blocks = pipeline.phases.interceptors()
        val execution = PipelineExecution(this, subject, blocks as List<PipelineContext<Any>.(Any) -> Unit>)
        executionStack.add(execution)

        if (executionStack.size == 1) {
            // machine is starting
            proceed()
        } else {
            // machine is forking
            throw PipelineControl.Continue
        }
    }

    fun proceed(): Nothing {
        // loop should catch all exceptions and convert them into fails
        while (loop());

        // loop can exit if it was paused or completed
        if (executionStack.isEmpty())
            throw PipelineControl.Completed
        else
            throw PipelineControl.Paused
    }

    fun loop(): Boolean {
        // Get current executing pipeline, or finish
        if (executionStack.isEmpty())
            return false
        val execution = executionStack[executionStack.lastIndex]

        // get current block index
        val blockIndex = execution.blockIndex

        // unwinding done, pop current pipeline execution
        if (blockIndex == -1) {
            executionStack.removeAt(executionStack.lastIndex)
            if (executionStack.isEmpty())
                return false

            // if FinishedAll or Failed, continue unwinding on next pipeline
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (execution.state) {
                PipelineState.FinishedAll -> {
                    val nextExecution = executionStack[executionStack.lastIndex]
                    // begin unwinding
                    nextExecution.blockIndex--
                    // propagate FinishedAll state
                    nextExecution.state = PipelineState.FinishedAll
                }
                PipelineState.Failed -> {
                    val nextExecution = executionStack[executionStack.lastIndex]
                    markFailed(nextExecution, execution.exception!!)
                }
            }
            return true
        }

        // current pipeline finished, mark as Finished for unwinding and continue
        if (blockIndex == execution.size) {
            execution.blockIndex--
            execution.state = PipelineState.Finished
            return true
        }

        when (execution.state) {
            PipelineState.FinishedAll,
            PipelineState.Finished -> {
                // did we finish unwinding this block?
                val successes = execution.blockSuccesses[blockIndex]
                if (successes == null || successes.isEmpty()) {
                    // yes, remove it and go backwards to previous block
                    execution.blockIndex--
                    return true
                }
                // no, pop and execute last success action
                return runAction(execution, successes.removeAt(successes.lastIndex))
            }
            PipelineState.Failed -> {
                // did we finish unwinding this block?
                val failures = execution.blockFailures[blockIndex]
                if (failures == null || failures.isEmpty()) {
                    // yes, remove it and go backwards to previous block
                    execution.blockIndex--
                    return true
                }
                // no, pop and execute last failure action
                return runAction(execution, failures.removeAt(failures.lastIndex))
            }
            PipelineState.Executing -> {
                execution.repeatIndex = execution.blockIndex
                // schedule next block
                execution.blockIndex++
                // execute current block's function
                return runAction(execution, execution.functions[blockIndex])
            }
        }
    }

    private fun runAction(execution: PipelineExecution, function: PipelineContext<Any>.(Any) -> Unit): Boolean {
        try {
            // execute an action
            execution.function(execution.subject)
            // finished naturally, just continue
            return true
        } catch (e: Throwable) {
            when (e) {
                is PipelineControl.Continue -> return true
                is PipelineControl.Paused -> return false
                is PipelineControl.Completed -> {
                    execution.state = PipelineState.Finished
                    return true
                }
                is AssertionError -> throw e
                else -> {
                    markFailed(execution, e)
                    return true
                }
            }
        }
    }

    fun pause(): Nothing {
        throw PipelineControl.Paused
    }

    private fun markFailed(execution: PipelineExecution, exception: Throwable) {
        execution.blockIndex--
        execution.state = PipelineState.Failed
        val lastException = execution.exception
        if (lastException != null)
            lastException.addSuppressed(exception)
        else
            execution.exception = exception
    }

    fun fail(exception: Throwable): Nothing {
        markFailed(executionStack.last(), exception)
        throw PipelineControl.Continue
    }

    fun finish(): Nothing {
        executionStack.last().state = PipelineState.Finished
        throw PipelineControl.Continue
    }

    fun finishAll(): Nothing {
        executionStack.last().state = PipelineState.FinishedAll
        throw PipelineControl.Continue
    }
}

