/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import android.provider.SyncStateContract.Helpers.update
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel( //AndroidViewModel dziala tak samo, ale
        val database: SleepDatabaseDao, //Dzieki temu otrzymuje dostep do contextu jako parametr
        application: Application) : AndroidViewModel(application) {

    //Pozwala anulowac wszystkie coroutines wystartowane przez ViewModel
    //Kiedy ViewModel nie jest dluzej uzywany i zniszczyc je
    private var viewModelJob = Job()
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel() //Clean coroutines
    }
    //Scope - aby pobrac scope pytamy o instancje scope
    //W scope wybieramy context w ktorym ma byc watek
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    //Zmienna do przetrzymania obecnej nocy
    private var tonight = MutableLiveData<SleepNight?>()

    private val nights = database.getAllNights()

    //String - wykonywane jest za kazdym razem kiedy nights otrzymaja nowe dane z bazy danych
    //Z klasy transformujacej parsujemy to na funkcje mapujaca, definiuje funkcje jako wywolanie formatNights
    val nightsString = Transformations.map(nights) { nights ->
        //Przekazuje noce i obiekt zasobow, poniewaz da to dostep do zasobow stringa
        formatNights(nights, application.resources)
    }

    //Ustawianie visibility buttonow po kliknieciu, potrzebny data binding w xml
    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

    val navigateToSleepQuality: LiveData<SleepNight>
        get() = _navigateToSleepQuality
    //Resetuje zmienna nawigacji
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }
    //Suspend, poniewaz chcemy wywolac ja z srodka coroutine i nie blokowac
    //Zwracamy sleepNight lub nulla
    private suspend fun getTonightFromDatabase(): SleepNight? {
        //Tworze inna coroutine uzywajac IO dispatcher
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            //Jesli startTime i endTime sa takie same
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }
    }

    fun onStartTracking() {
        uiScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }
    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            //@launch - określenie, z której funkcji spośród kilku zagnieżdżonych zwraca ta instrukcja
            //czyli tutaj jest return z launch, a nie z lambdy!
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }
    fun onClear() {
        uiScope.launch {
            clear()
            tonight.value = null

            _showSnackbarEvent.value = true
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }
}

