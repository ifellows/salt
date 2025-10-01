package com.dev.salt.data

import androidx.room.*

@Entity(
    tableName = "test_configurations",
    indices = [Index(value = ["survey_id", "test_id"], unique = true)]
)
data class TestConfiguration(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "survey_id")
    val surveyId: Long,
    @ColumnInfo(name = "test_id")
    val testId: String,
    @ColumnInfo(name = "test_name")
    val testName: String,
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int
)

@Dao
interface TestConfigurationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTestConfiguration(testConfiguration: TestConfiguration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTestConfigurations(testConfigurations: List<TestConfiguration>)

    @Query("SELECT * FROM test_configurations WHERE survey_id = :surveyId ORDER BY display_order")
    fun getTestConfigurationsBySurveyId(surveyId: Long): List<TestConfiguration>

    @Query("SELECT * FROM test_configurations WHERE survey_id = :surveyId AND enabled = 1 ORDER BY display_order")
    fun getEnabledTestConfigurations(surveyId: Long): List<TestConfiguration>

    @Query("SELECT * FROM test_configurations WHERE survey_id = :surveyId AND test_id = :testId LIMIT 1")
    fun getTestConfiguration(surveyId: Long, testId: String): TestConfiguration?

    @Query("DELETE FROM test_configurations WHERE survey_id = :surveyId")
    fun deleteTestConfigurationsBySurveyId(surveyId: Long)

    @Query("DELETE FROM test_configurations")
    fun deleteAllTestConfigurations()
}
