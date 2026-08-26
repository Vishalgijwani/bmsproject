package com.rhythm.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rhythm.app.data.ExclusionStore
import com.rhythm.app.data.RhythmDatabase
import com.rhythm.app.data.SessionEntity
import com.rhythm.app.data.SessionIngester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RhythmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = RhythmDatabase.get(application)
    private val dao = db.sessionDao()
    private val exclusionStore = ExclusionStore(application)

    // Rebuilt whenever the exclusion list changes, so the drop-list stays current.
    @Volatile
    private var ingester = SessionIngester(application, exclusionStore.getExcluded())

    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions.asStateFlow()

    private val _sessionCount = MutableStateFlow(0)
    val sessionCount: StateFlow<Int> = _sessionCount.asStateFlow()

    private val _excludedPackages = MutableStateFlow<Set<String>>(emptySet())
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    init {
        _excludedPackages.value = exclusionStore.getExcluded()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val all = dao.getAll()
            _sessions.value = all
            _sessionCount.value = all.size
        }
    }

    /** Trigger an immediate manual ingest. */
    fun ingestNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastEnd = dao.maxEndTime() ?: 0L
            val newSessions = ingester.fetchSessions(lastEnd)
            if (newSessions.isNotEmpty()) {
                dao.insertAll(newSessions)
            }
            refresh()
        }
    }

    fun setExcluded(pkgs: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val old = exclusionStore.getExcluded()
            val newlyExcluded = pkgs - old
            for (pkg in newlyExcluded) {
                dao.deleteByPackage(pkg)
            }
            exclusionStore.setExcluded(pkgs)
            _excludedPackages.value = pkgs
            ingester = SessionIngester(getApplication(), pkgs)
            refresh()
        }
    }

    fun deleteAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteAll()
            refresh()
        }
    }

    fun resolveLabel(pkg: String): String = ingester.resolveLabel(pkg)

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RhythmViewModel(app) as T
        }
    }
}
