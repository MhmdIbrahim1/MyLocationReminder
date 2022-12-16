package com.udacity.project4.locationreminders.reminderslist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.udacity.project4.locationreminders.MainCoroutineRule
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
class RemindersListViewModelTest {
    @get: Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get: Rule
    var mainCoroutineRule = MainCoroutineRule()
    private lateinit var remindersListViewModel: RemindersListViewModel
    private lateinit var datasource: FakeDataSource


    @Before
    fun setUp() {
        stopKoin()
        datasource = FakeDataSource()
        remindersListViewModel =
            RemindersListViewModel(ApplicationProvider.getApplicationContext(), datasource)
    }

    @Test
    fun check_loading() = mainCoroutineRule.runBlockingTest {
        mainCoroutineRule.pauseDispatcher()
        remindersListViewModel.loadReminders()
        MatcherAssert.assertThat(
            remindersListViewModel.showLoading.getOrAwaitValue(),
            Matchers.`is`(true)
        )
        mainCoroutineRule.resumeDispatcher()
        MatcherAssert.assertThat(
            remindersListViewModel.showLoading.getOrAwaitValue(),
            Matchers.`is`(false)
        )
    }
}