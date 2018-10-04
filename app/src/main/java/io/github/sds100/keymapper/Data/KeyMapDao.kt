package io.github.sds100.keymapper.Data

import androidx.lifecycle.LiveData
import androidx.room.*
import io.github.sds100.keymapper.ActionTypeTypeConverter
import io.github.sds100.keymapper.KeyMap
import io.github.sds100.keymapper.TypeConverters.TriggerListTypeConverter

/**
 * Created by sds100 on 05/09/2018.
 */

@Dao
abstract class KeyMapDao {
    companion object {
        const val TABLE_NAME = "keymaps"
        const val KEY_ID = "id"
        const val KEY_TRIGGER_LIST = "trigger_list"
        const val KEY_ACTION_TYPE = "action_type"
        const val KEY_ACTION_DATA = "action_data"
    }

    @Query("SELECT * FROM $TABLE_NAME")
    abstract fun getAllKeyMaps(): LiveData<List<KeyMap>>

    @Insert(onConflict = OnConflictStrategy.FAIL)
    abstract fun insert(vararg keyMap: KeyMap): LongArray

    @Delete
    abstract fun delete(vararg keyMap: KeyMap)

    @Query("DELETE FROM $TABLE_NAME WHERE $KEY_ID in (:id)")
    abstract fun deleteById(vararg id: Long)

    @Update
    abstract fun update(vararg keyMap: KeyMap)
}