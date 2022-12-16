package com.udacity.project4.locationreminders.data

import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result

//Use FakeDataSource that acts as a test double to the LocalDataSource
class FakeDataSource(var reminders: MutableList<ReminderDTO>? = mutableListOf()) : ReminderDataSource {

    private var shouldReturnError = false

    override suspend fun getReminders(): Result<List<ReminderDTO>> {
        if (shouldReturnError) {
            return Result.Error("Test Exception")
        }
        reminders?.let {
            return Result.Success(ArrayList(it))
        }
        return Result.Error("No reminders found")
    }


    override suspend fun saveReminder(reminder: ReminderDTO) {
        reminders?.add(reminder)
    }

    override suspend fun getReminder(id: String): Result<ReminderDTO> {
        if (shouldReturnError)
            return Result.Error("Test Exception")
        val found = reminders?.first { it.id == id }
        return if (found != null)
            Result.Success(found)
        else
            Result.Error("Reminder $id not found")
    }

    override suspend fun deleteAllReminders() {
        reminders?.clear()
    }


    fun setReturnError(value: Boolean) {
        shouldReturnError = value
    }
}