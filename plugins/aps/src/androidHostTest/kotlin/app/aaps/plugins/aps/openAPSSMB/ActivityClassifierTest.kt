package app.aaps.plugins.aps.openAPSSMB

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ActivityClassifierTest {

    @BeforeEach fun resetPhoneActivity() {
        ActivityClassifier.phoneActivity = ActivityClassifier.PHONE_STILL
    }

    @Test fun `bicycle activity returns AEROBIC`() {
        val result = ActivityClassifier.classify(steps15m = 0, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_BICYCLE)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `walking activity returns AEROBIC`() {
        val result = ActivityClassifier.classify(steps15m = 0, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_WALKING)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `running activity returns AEROBIC`() {
        val result = ActivityClassifier.classify(steps15m = 0, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_RUNNING)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `high steps without phone signal returns AEROBIC`() {
        val result = ActivityClassifier.classify(steps15m = 200, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_STILL)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `steps at threshold 151 returns AEROBIC`() {
        val result = ActivityClassifier.classify(steps15m = 151, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_STILL)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `low steps and high HR returns RESISTANCE`() {
        // restingHr=60, threshold = 60 * 1.25 = 75.0, avgHr=80 > 75 → RESISTANCE
        val result = ActivityClassifier.classify(steps15m = 10, avgHrLast15m = 80.0, phoneActivity = ActivityClassifier.PHONE_STILL, restingHr = 60)
        assertThat(result).isEqualTo(ActivityClass.RESISTANCE)
    }

    @Test fun `HR exactly at threshold does not trigger RESISTANCE`() {
        // restingHr=60, threshold=75.0, avgHr=75.0 is NOT > 75.0
        val result = ActivityClassifier.classify(steps15m = 10, avgHrLast15m = 75.0, phoneActivity = ActivityClassifier.PHONE_STILL, restingHr = 60)
        assertThat(result).isEqualTo(ActivityClass.REST)
    }

    @Test fun `high steps with high HR returns AEROBIC not RESISTANCE`() {
        val result = ActivityClassifier.classify(steps15m = 200, avgHrLast15m = 90.0, phoneActivity = ActivityClassifier.PHONE_STILL, restingHr = 60)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }

    @Test fun `zero HR does not trigger RESISTANCE`() {
        val result = ActivityClassifier.classify(steps15m = 5, avgHrLast15m = 0.0, phoneActivity = ActivityClassifier.PHONE_STILL)
        assertThat(result).isEqualTo(ActivityClass.REST)
    }

    @Test fun `no activity returns REST`() {
        val result = ActivityClassifier.classify(steps15m = 0, avgHrLast15m = 60.0, phoneActivity = ActivityClassifier.PHONE_STILL)
        assertThat(result).isEqualTo(ActivityClass.REST)
    }

    @Test fun `default phoneActivity is STILL`() {
        assertThat(ActivityClassifier.phoneActivity).isEqualTo(ActivityClassifier.PHONE_STILL)
    }

    @Test fun `phoneActivity singleton can be updated`() {
        ActivityClassifier.phoneActivity = ActivityClassifier.PHONE_WALKING
        val result = ActivityClassifier.classify(steps15m = 0, avgHrLast15m = 0.0)
        assertThat(result).isEqualTo(ActivityClass.AEROBIC)
    }
}
