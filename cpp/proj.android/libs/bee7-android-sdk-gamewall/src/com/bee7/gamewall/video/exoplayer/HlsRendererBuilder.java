/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bee7.gamewall.video.exoplayer;

import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;
import android.widget.TextView;

import com.bee7.sdk.publisher.GameWallConfiguration;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.chunk.VideoFormatSelectorUtil;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.metadata.Id3Parser;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.text.eia608.Eia608TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;

import java.io.IOException;
import java.util.Map;

/**
 * A {@link com.bee7.gamewall.video.exoplayer.DemoPlayer.RendererBuilder} for HLS.
 */
public class HlsRendererBuilder implements DemoPlayer.RendererBuilder, ManifestCallback<HlsPlaylist> {

    private static final int REQUESTED_BUFFER_SIZE = 18 * 1024 * 1024;
    //private static final long REQUESTED_BUFFER_DURATION_MS = 20000;

    private final Context context;
    private final String userAgent;
    private final String url;
    private final AudioCapabilities audioCapabilities;
    private final GameWallConfiguration.VideoPrequalGlobalConfig videoPrequalGlobalConfig;

    private DemoPlayer player;
    private DemoPlayer.RendererBuilderCallback callback;

    public HlsRendererBuilder(Context context, String userAgent, String url, AudioCapabilities audioCapabilities,
                              GameWallConfiguration.VideoPrequalGlobalConfig videoPrequalGlobalConfig) {
        this.context = context;
        this.userAgent = userAgent;
        this.url = url;
        this.audioCapabilities = audioCapabilities;
        this.videoPrequalGlobalConfig = videoPrequalGlobalConfig;
    }

    @Override
    public void buildRenderers(DemoPlayer player, DemoPlayer.RendererBuilderCallback callback) {
        this.player = player;
        this.callback = callback;
        HlsPlaylistParser parser = new HlsPlaylistParser();
        ManifestFetcher<HlsPlaylist> playlistFetcher = new ManifestFetcher<HlsPlaylist>(url,
                new DefaultUriDataSource(context, userAgent), parser);
        playlistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
    }

    @Override
    public void onSingleManifestError(IOException e) {
        callback.onRenderersError(e);
    }

    @Override
    public void onSingleManifest(HlsPlaylist manifest) {
        Handler mainHandler = player.getMainHandler();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        int[] variantIndices = null;
        if (manifest instanceof HlsMasterPlaylist) {
            HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) manifest;
            try {
                variantIndices = VideoFormatSelectorUtil.selectVideoFormatsForDefaultDisplay(
                        context, masterPlaylist.variants, null, false);
            } catch (MediaCodecUtil.DecoderQueryException e) {
                callback.onRenderersError(e);
                return;
            }
        }

        DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        HlsChunkSource chunkSource = new HlsChunkSource(dataSource, url, manifest, bandwidthMeter,
                variantIndices, HlsChunkSource.ADAPTIVE_MODE_SPLICE, audioCapabilities, videoPrequalGlobalConfig);
        HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true, 3, REQUESTED_BUFFER_SIZE,
                videoPrequalGlobalConfig.getTargetBufferDurationMillis(), mainHandler, player, DemoPlayer.TYPE_VIDEO);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 2000, mainHandler, player, 50);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

        MetadataTrackRenderer<Map<String, Object>> id3Renderer =
                new MetadataTrackRenderer<Map<String, Object>>(sampleSource, new Id3Parser(),
                        player.getId3MetadataRenderer(), mainHandler.getLooper());

        Eia608TrackRenderer closedCaptionRenderer = new Eia608TrackRenderer(sampleSource, player,
                mainHandler.getLooper());


        TrackRenderer[] renderers = new TrackRenderer[DemoPlayer.RENDERER_COUNT];
        renderers[DemoPlayer.TYPE_VIDEO] = videoRenderer;
        renderers[DemoPlayer.TYPE_AUDIO] = audioRenderer;
        renderers[DemoPlayer.TYPE_TIMED_METADATA] = id3Renderer;
        renderers[DemoPlayer.TYPE_TEXT] = closedCaptionRenderer;
        callback.onRenderers(null, null, renderers);
    }

}
