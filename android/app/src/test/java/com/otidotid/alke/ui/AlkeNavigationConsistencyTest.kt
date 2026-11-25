package com.otidotid.alke.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ALKE display labels must not drift across dock, editor and All Modules. */
class AlkeNavigationConsistencyTest {
    @Test fun approvedFiveTabNamesMatchTheSingleModuleAliasContract() {
        assertEquals("Today", alkeModuleAlias(Destination.Today))
        assertEquals("Train", alkeModuleAlias(Destination.Workouts))
        assertEquals("Progress", alkeModuleAlias(Destination.Trends))
        assertEquals("Insight", alkeModuleAlias(Destination.Intelligence))
        assertEquals("Me", alkeModuleAlias(Destination.More))
        assertEquals("Live HR", alkeModuleAlias(Destination.Live))
        assertEquals("Fitness Age", alkeModuleAlias(Destination.Health))
    }

    @Test fun newAllModulesCatalogHasNoDuplicateRoutesAndHidesMeShortcuts() {
        val pinned = BottomBarModuleStore.defaults
        val allModules = alkeAllModulesGroups(pinned).flatMap { it.items }
        assertEquals(allModules.size, allModules.distinct().size)
        for (destination in pinned) assertFalse(destination in allModules)
        for (destination in listOf(
            Destination.Settings, Destination.Devices, Destination.DataSources, Destination.BackupSync,
        )) assertFalse(destination in allModules)
        // User-facing features have NOT been dropped; the catalog keeps other destinations.
        assertTrue(Destination.Live in allModules)
        assertTrue(Destination.Sleep in allModules)
        // Legacy Insight routes remain deep-link aliases, not separate catalog modules.
        assertFalse(Destination.Insights in allModules)
        assertFalse(Destination.Health in allModules)
        val unpinnedModules = alkeAllModulesGroups(emptyList()).flatMap { it.items }
        assertTrue(Destination.Intelligence in unpinnedModules)
        assertFalse(Destination.Insights in unpinnedModules)
    }

    @Test fun unpinnedTrainCanBeDiscoveredOnceWithoutSeparateHistoryModule() {
        val allModules = alkeAllModulesGroups(emptyList()).flatMap { it.items }
        assertEquals(1, allModules.count { it == Destination.Workouts })
        assertFalse(Destination.entries.any { it.route == "alke_workout_history" })
    }
}
