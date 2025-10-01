package com.dev.salt.data

import androidx.room.*

@Entity(
    tableName = "test_results",
    indices = [Index(value = ["survey_id", "test_id"], unique = true)]
)
data class TestResult(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "survey_id")
    val surveyId: String,
    @ColumnInfo(name = "test_id")
    val testId: String,
    @ColumnInfo(name = "test_name")
    val testName: String,
    @ColumnInfo(name = "result")
    val result: String, // "positive", "negative", "indeterminate", "not_performed"
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long = System.currentTimeMillis()
)

@Dao
interface TestResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTestResult(testResult: TestResult)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTestResults(testResults: List<TestResult>)

    @Query("SELECT * FROM test_results WHERE survey_id = :surveyId ORDER BY recorded_at")
    fun getTestResultsBySurveyId(surveyId: String): List<TestResult>

    @Query("SELECT * FROM test_results WHERE survey_id = :surveyId AND test_id = :testId LIMIT 1")
    fun getTestResult(surveyId: String, testId: String): TestResult?

    @Query("DELETE FROM test_results WHERE survey_id = :surveyId")
    fun deleteTestResultsBySurveyId(surveyId: String)

    @Query("DELETE FROM test_results")
    fun deleteAllTestResults()

    @Update
    fun updateTestResult(testResult: TestResult)
}
