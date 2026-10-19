package paige.navic.domain.models.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class NavbarConfig(
	val tabs: List<NavbarTab>,
	val version: Int
) {
	companion object {
		const val KEY = "navbarConfig"
		const val VERSION = 11
		val default = NavbarConfig(
			tabs = listOf(
				// Svirka: web nav (Songs, Playlists, Friends) + Library for albums/artists.
				// Explore is part of Songs' search now; the Import screen stays for shared links and uploads.
				NavbarTab(NavbarTab.Id.SONGS, true),
				NavbarTab(NavbarTab.Id.PLAYLISTS, true),
				NavbarTab(NavbarTab.Id.LIBRARY, true),
				NavbarTab(NavbarTab.Id.IMPORT, false),
				NavbarTab(NavbarTab.Id.FRIENDS, true),
				NavbarTab(NavbarTab.Id.ALBUMS, false),
				NavbarTab(NavbarTab.Id.ARTISTS, false),
				NavbarTab(NavbarTab.Id.SEARCH, false),
				NavbarTab(NavbarTab.Id.GENRES, false),
				NavbarTab(NavbarTab.Id.RADIOS, false)
			),
			version = VERSION
		)
	}
}
