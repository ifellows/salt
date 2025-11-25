package com.dev.salt.data

import androidx.room.*

@Entity(tableName = "lab_test_configurations")
data class LabTestConfiguration(
    @PrimaryKey
    val id: Long,
    @ColumnInfo(name = "test_name")
    val testName: String,
    @ColumnInfo(name = "test_code")
    val testCode: String?,
    @ColumnInfo(name = "test_type")
    val testType: String, // "dropdown" or "numeric"
    @ColumnInfo(name = "options")
    val options: String?, // JSON array for dropdown options
    @ColumnInfo(name = "min_value")
    val minValue: Double?,
    @ColumnInfo(name = "max_value")
    val maxValue: Double?,
    @ColumnInfo(name = "unit")
    val unit: String?,
    @ColumnInfo(name = "jexl_condition")
    val jexlCondition: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "display_order")
    val displayOrder: Int
)

@Dao
interface LabTestConfigurationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLabTestConfiguration(config: LabTestConfiguration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLabTestConfigurations(configs: List<LabTestConfiguration>)

    @Query("SELECT * FROM lab_test_configurations WHERE is_active = 1 ORDER BY display_order")
    fun getActiveLabTestConfigurations(): List<LabTestConfiguration>

    @Query("SELECT * FROM lab_test_configurations ORDER BY display_order")
    fun getAllLabTestConfigurations(): List<LabTestConfiguration>

    @Query("SELECT * FROM lab_test_configurations WHERE id = :id LIMIT 1")
    fun getLabTestConfigurationById(id: Long): LabTestConfiguration?

    @Query("DELETE FROM lab_test_configurations")
    fun deleteAllLabTestConfigurations()
}
