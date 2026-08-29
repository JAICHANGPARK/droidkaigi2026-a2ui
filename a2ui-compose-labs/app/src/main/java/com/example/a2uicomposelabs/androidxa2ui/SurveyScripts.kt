package com.example.a2uicomposelabs.androidxa2ui

// The survey, written twice — same arrangement as BookingScripts.kt, and the same shared
// catalogId within the pair.
//
// The survey is the harder of the two screens for androidx.a2ui: of the four controls it needs,
// material3-a2ui ships exactly one.

// ---------------------------------------------------------------- survey, androidx v0.9.1

val SurveyAndroidxScript: List<String> =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"survey","catalogId":"$SURVEY_CATALOG_ID","sendDataModel":true}}""",
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"survey","path":"/","value":{"visitRating":0,"drinkRating":0,"comeBack":false,"comments":""}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["title","q1","q2","q3","q4","submit"]},{"id":"title","component":"Text","text":"Cafe satisfaction survey"}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"q1","component":"Question","text":"How would you rate your overall visit today?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/visitRating"},"max":5}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"q2","component":"Question","text":"How would you rate the quality of your drinks?","required":true,"children":["a2"]},{"id":"a2","component":"StarRating","value":{"path":"/drinkRating"},"max":5}]}}""",
        // The one input control material3-a2ui does ship, used unmodified.
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"q3","component":"Question","text":"Would you visit us again?","required":false,"children":["a3"]},{"id":"a3","component":"CheckBox","label":"Yes, I would come back","value":{"path":"/comeBack"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"q4","component":"Question","text":"Any suggestions for improvements?","required":false,"children":["a4"]},{"id":"a4","component":"TextField","label":"Your comments","text":{"path":"/comments"}}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"survey","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"submitSurvey","context":{"visitRating":{"path":"/visitRating"},"drinkRating":{"path":"/drinkRating"},"comeBack":{"path":"/comeBack"},"comments":{"path":"/comments"}}}}},{"id":"submit_label","component":"Text","text":"Submit feedback"}]}}""",
    )

// ---------------------------------------------------------------- survey, spec v1.0

val SurveyV1Script: List<String> =
    listOf(
        """{"version":"v1.0","createSurface":{"surfaceId":"survey","catalogId":"$SURVEY_CATALOG_ID","components":[{"id":"root","component":"Card","children":["title","q1","q2","q3","q4","submit"]}],"dataModel":{"visitRating":0,"drinkRating":0,"comeBack":false,"comments":""}}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"title","component":"Text","text":"Cafe satisfaction survey"}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"q1","component":"Question","text":"How would you rate your overall visit today?","required":true,"children":["a1"]},{"id":"a1","component":"StarRating","value":{"path":"/visitRating"},"max":5}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"q2","component":"Question","text":"How would you rate the quality of your drinks?","required":true,"children":["a2"]},{"id":"a2","component":"StarRating","value":{"path":"/drinkRating"},"max":5}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"q3","component":"Question","text":"Would you visit us again?","required":false,"children":["a3"]},{"id":"a3","component":"CheckBox","label":"Yes, I would come back","value":{"path":"/comeBack"}}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"q4","component":"Question","text":"Any suggestions for improvements?","required":false,"children":["a4"]},{"id":"a4","component":"TextField","label":"Your comments","text":{"path":"/comments"}}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"survey","components":[{"id":"submit","component":"Button","label":"Submit feedback","action":{"name":"submitSurvey","context":{"visitRating":{"path":"/visitRating"},"drinkRating":{"path":"/drinkRating"},"comeBack":{"path":"/comeBack"},"comments":{"path":"/comments"}}}}]}}""",
    )
