package com.otidotid.alke.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepsAiValidatorClientTest {
    @Test fun keepPreservesRawSteps() {
        val json = """{"stepsValidation":{"schemaVersion":"steps-validator-v1","day":"2026-11-19","rawSteps":8000,"status":"KEEP","factor":1.0,"finalSteps":8000,"confidence":0.92,"reason":"Consistent","model":"openai/gpt-oss-20b"}}"""
        val result = StepsAiValidatorClient.parse(json, 8000)!!
        assertEquals(8000, result.finalSteps)
        assertEquals(StepsAiValidatorClient.Status.KEEP, result.status)
    }

    @Test fun highConfidenceAdjustmentIsAccepted() {
        val json = """{"stepsValidation":{"schemaVersion":"steps-validator-v1","day":"2026-11-19","rawSteps":10000,"status":"ADJUST","factor":0.8,"finalSteps":8000,"confidence":0.87,"reason":"Cycling contradiction","model":"openai/gpt-oss-20b"}}"""
        assertEquals(8000, StepsAiValidatorClient.parse(json, 10000)!!.finalSteps)
    }

    @Test fun lowConfidenceAdjustmentIsRejected() {
        val json = """{"stepsValidation":{"schemaVersion":"steps-validator-v1","day":"2026-11-19","rawSteps":10000,"status":"ADJUST","factor":0.8,"finalSteps":8000,"confidence":0.79,"reason":"Weak evidence","model":"openai/gpt-oss-20b"}}"""
        assertNull(StepsAiValidatorClient.parse(json, 10000))
    }

    @Test fun mismatchedRawStepsIsRejected() {
        val json = """{"stepsValidation":{"schemaVersion":"steps-validator-v1","day":"2026-11-19","rawSteps":9000,"status":"KEEP","factor":1.0,"finalSteps":9000,"confidence":0.9,"reason":"Consistent","model":"openai/gpt-oss-20b"}}"""
        assertNull(StepsAiValidatorClient.parse(json, 8000))
    }
}
