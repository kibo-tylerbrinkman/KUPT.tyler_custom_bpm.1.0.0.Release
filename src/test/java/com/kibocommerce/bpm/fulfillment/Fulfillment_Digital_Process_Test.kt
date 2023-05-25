package com.kibocommerce.bpm.fulfillment

import org.jbpm.test.JbpmJUnitBaseTestCase
import org.junit.Before
import org.junit.Test
import org.kie.api.runtime.KieSession
import org.kie.api.runtime.process.WorkflowProcessInstance
import org.kie.api.task.TaskService
import org.kie.api.task.model.Status
import org.slf4j.LoggerFactory
import kotlin.collections.HashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Fulfillment_Digital_Process_Test : JbpmJUnitBaseTestCase(true, false) {
    private var kieSession: KieSession? = null
    private var taskService: TaskService? = null

    @Before
    fun init() {
        createRuntimeManager("com/kibocommerce/bpm/fulfillment/FulfillmentProcess-Digital.bpmn")
        val runtimeEngine = getRuntimeEngine(null)
        kieSession = runtimeEngine.kieSession
        taskService = runtimeEngine.taskService
    }

    @Test
    fun setFulfillDigitalItemsActions() {
        val wpi = createProcess()

        wpi.signalEvent("execute", null)

        assertCurrentState(wpi, "IN_PROGRESS")
    }

    @Test
    fun setPaymentCaptureActions() {
        val wpi = createProcess()

        wpi.signalEvent("execute", null)
        assertCurrentState(wpi, "IN_PROGRESS")
        wpi.signalEvent("execute", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun fulfill() {
        val wpi = createProcess()

        wpi.signalEvent("fulfilled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "COMPLETED")
    }

    @Test
    fun cancel() {
        val wpi = createProcess()

        wpi.signalEvent("canceled", null)

        assertProcessInstanceNotActive(wpi.id, kieSession)
        assertCurrentState(wpi, "CANCELED")
    }

    @Test
    fun customerCare() {
        val wpi = createProcess()

        wpi.signalEvent("customer_care", null)
        assertCurrentState(wpi, "CUSTOMER_CARE")
    }

    private fun createProcess(): WorkflowProcessInstance {
        val processParam = mapOf("initiator" to "john")
        val processInstance = kieSession!!.startProcess("fulfillment.FulfillmentProcess-Digital", processParam)

        assertTrue(processInstance is WorkflowProcessInstance)
        assertNodeExists(
                processInstance,
                "Set Fulfill Digital Items Actions",
                "Set Payment Capture Actions"
        )
        assertNodeTriggered(processInstance.id, null, "Set Fulfill Digital Items Actions")
        assertEquals("IN_PROGRESS", processInstance.getVariable("currentState"))

        logger.info("Created process {}", processInstance.processName)
        return processInstance
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Fulfillment_Digital_Process_Test::class.java)
    }
}