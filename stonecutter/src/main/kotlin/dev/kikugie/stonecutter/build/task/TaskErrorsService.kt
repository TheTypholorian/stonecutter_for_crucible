package dev.kikugie.stonecutter.build.task

import dev.kikugie.commons.then
import dev.kikugie.stitcher.issue.ProblemReport
import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener

internal abstract class TaskErrorsService : BuildService<BuildServiceParameters.None>, OperationCompletionListener {
    abstract val errors: MapProperty<Int, Boolean>

    override fun onFinish(event: FinishEvent?) {
    }

    fun isNew(report: ProblemReport): Boolean = synchronized(errors) {
        val hash = report.hashCode()
        if (errors.getting(hash).isPresent) false
        else errors.put(hash, true) then true
    }

    companion object {
        const val NAME: String = "StonecutterTaskErrors"
    }
}