package co.jp.r.horrorstoryreader;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import java.io.StringReader;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

@SpringBootApplication
public class HorrorStoryReaderApplication implements CommandLineRunner {

    private final String token;
    private Speaker speaker = Speaker.Zundamon_Normal;

    private AudioPlayerManager playerManager;
    private AudioPlayer player;
    private LavaPlayerAudioProvider provider;
    private TrackScheduler scheduler;
    private HorrorStories horrorStories;

    private final DiscordClient client;
    private final GatewayDiscordClient gateway;
    
    private Snowflake selfId;

    private final String wavDirectory;
    
    public HorrorStoryReaderApplication(
            @Value("${discord.token}") final String token,
            @Value("${wav-directory}") final String wavDirectory,
            final HorrorStories horrorStories) {
        this.token = token;
        this.client = DiscordClient.create(token);
        this.gateway = client.login().block();
        this.wavDirectory = wavDirectory;
        this.horrorStories = horrorStories;
    }

    public static void main(String[] args) {
        SpringApplication.run(HorrorStoryReaderApplication.class, args);
    }

    @Override
    public void run(String... args) {

        init();

        // JoinとDisconnectの機構はvoicevox-botから引っ張ってきている。
        gateway.on(MessageCreateEvent.class).subscribe(e -> {
            final Message message = e.getMessage();
            if (message.getAuthor().get().isBot()) return;
            if ("お帰りください".equals(message.getData().content())) {
                final Member member = e.getMember().orElse(null);
                if (member != null) {
                    final VoiceState voiceState = member.getVoiceState().block();
                    if (voiceState != null) {
                        final VoiceChannel channel = voiceState.getChannel().block();
                        if (channel != null) {
                            stopAudioPlayer();
                            channel.sendDisconnectVoiceState().block();
                        }
                    }
                }
            }
        });

        // join event
        gateway.on(MessageCreateEvent.class).subscribe(e -> {
            final Message message = e.getMessage();
            // あとでなおす
            if (message.getAuthor().get().isBot()) return;
            if ("怖い話が聞きたいです".equals(message.getData().content())) {
                final Member member = e.getMember().orElse(null);
                if (member != null) {
                    final VoiceState voiceState = member.getVoiceState().block();
                    if (voiceState != null) {
                        final VoiceChannel channel = voiceState.getChannel().block();
                        if (channel != null) {

                            channel.join(spec -> {
                                spec.setProvider(provider);
                            }).block();
                            stopAudioPlayer();
                        }
                    }
                }
            }
        });


        // 停止
        gateway.on(MessageCreateEvent.class).subscribe(e -> {
            final Message message = e.getMessage();
            if (message.getAuthor().get().isBot()) return;
            if ("止めてください".equals(message.getData().content())) {
                final Member member = e.getMember().orElse(null);
                if (member != null) {
                    final VoiceState voiceState = member.getVoiceState().block();
                    if (voiceState != null) {
                        final VoiceChannel channel = voiceState.getChannel().block();
                        if (channel != null) {
                            pauseAudioPlayer();
                        }
                    }
                }
            }
        });

        // 再開
        gateway.on(MessageCreateEvent.class).subscribe(e -> {
            final Message message = e.getMessage();
            if (message.getAuthor().get().isBot()) return;
            if ("再開してください".equals(message.getData().content())) {
                final Member member = e.getMember().orElse(null);
                if (member != null) {
                    final VoiceState voiceState = member.getVoiceState().block();
                    if (voiceState != null) {
                        final VoiceChannel channel = voiceState.getChannel().block();
                        if (channel != null) {
                            resumeAudioPlayer();
                        }
                    }
                }
            }
        });

        // 朗読イベント
        gateway.on(MessageCreateEvent.class).subscribe(e -> {
            final Message message = e.getMessage();
            if (message.getAuthor().get().isBot()) {
                return;
            }

            // VCにJoinしていなければ朗読はしない。
            if (!isSelfJoinVc(e.getMember().orElse(null))) return;

            if ("お話を聞かせてください".equals(message.getData().content())) {
                // ちゃんとサイクル管理すること
                String horrorStoryAudioPath = this.wavDirectory + horrorStories.getStories().get(RandomGenerator.getDefault().nextInt(horrorStories.getStories().size()));
                System.out.println(horrorStoryAudioPath);
                playerManager.loadItem(horrorStoryAudioPath, scheduler);
            }
        });

        gateway.onDisconnect().block();
    }

    // メソッド名が意味不明
    private boolean isSelfJoinVc(final Member member) {
        if (member != null) {
            final VoiceState voiceState = member.getVoiceState().block();
            if (voiceState != null) {
                final VoiceChannel channel = voiceState.getChannel().block();
                if (channel != null) {
                    return channel.isMemberConnected(selfId).block();
                }
            }
        }
        return false;
    }

    private void stopAudioPlayer() {
        player.stopTrack();
    }

    private void pauseAudioPlayer() {
        player.setPaused(true);
    }

    private void resumeAudioPlayer() {
        player.setPaused(false);
    }

    private boolean containsForbiddenCharacters(final String src) {
        if (!StringUtils.hasLength(src)) return true;
        if (src.startsWith("!")) return true;
        // URLを無視したい
        if (src.startsWith("http://") || src.startsWith("https://")) return true;
        return false;
    }

    private void init() {
        playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerLocalSource(playerManager);
        player = playerManager.createPlayer();
        provider = new LavaPlayerAudioProvider(player);
        scheduler = new TrackScheduler(player);
        selfId = gateway.getSelfId();
    }
}
