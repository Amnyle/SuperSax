package app.saxpractice.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import app.saxpractice.domain.program.ExerciseType
import app.saxpractice.ui.hub.HubEntry

/** Destinations added by later phases (tools, drills, library, rank...). */
fun NavGraphBuilder.extraDestinations(nav: NavHostController) = Unit

fun extraPhoneDrillRoute(type: ExerciseType): String? = null

@Composable
fun extraProgressEntries(nav: NavHostController): List<HubEntry> = emptyList()

@Composable
fun extraToolEntries(nav: NavHostController): List<HubEntry> = emptyList()
