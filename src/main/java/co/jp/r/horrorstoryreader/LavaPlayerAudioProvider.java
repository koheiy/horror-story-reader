package co.jp.r.horrorstoryreader;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import discord4j.voice.AudioProvider;
import java.nio.ByteBuffer;



public class LavaPlayerAudioProvider extends AudioProvider {
    private final AudioPlayer player;
    private final MutableAudioFrame frame;

    public LavaPlayerAudioProvider(final AudioPlayer player) {
        super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));
        frame = new MutableAudioFrame();
        frame.setBuffer(getBuffer());
        this.player = player;
    }

    @Override
    public boolean provide() {
        final boolean didProvide = player.provide(frame);
        if (didProvide) {
            getBuffer().flip();
        }
        return didProvide;
    }
}
