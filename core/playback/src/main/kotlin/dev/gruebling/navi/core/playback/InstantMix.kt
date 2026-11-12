package dev.gruebling.navi.core.playback

import dev.gruebling.navi.core.data.repository.MixRepository
import dev.gruebling.navi.core.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/** Starts instant mixes. Once started, [MixExtender] keeps the queue from running out. */
@Singleton
class InstantMix @Inject constructor(
    private val mixes: MixRepository,
    private val playback: PlaybackController,
) {
    enum class Outcome {
        Started,

        /** The server has no similar songs (e.g. no Last.fm set up, or nothing similar in the library). */
        NoSongs,

        /** The request failed (network). Trying again may work. */
        Failed,
    }

    /** Plays a mix around an artist. */
    suspend fun startFromArtist(artistId: String): Outcome =
        start(mixes.similarSongs(artistId)) { playback.playSongs(it, fromMix = true) }

    /** The current song keeps playing; everything else in the queue becomes songs similar to it. */
    suspend fun startFromCurrentSong(): Outcome {
        val current = playback.state.value.current ?: return Outcome.NoSongs
        return start(mixes.similarSongs(current.songId, current.artistId), playback::continueWithMix)
    }

    private inline fun start(result: Result<List<Song>>, play: (List<Song>) -> Unit): Outcome {
        val songs = result.getOrElse { return Outcome.Failed }.take(MIX_SIZE)
        if (songs.isEmpty()) return Outcome.NoSongs
        play(songs)
        return Outcome.Started
    }

    companion object {
        const val MIX_SIZE = 25
    }
}
