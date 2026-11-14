package com.aadith.mustre.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;

@GlideModule
public final class CustomGlideModule extends AppGlideModule {
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        long diskCacheSizeBytes = 100L * 1024L * 1024L; // 100 MB disk cache limit for images
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, "image_cache", diskCacheSizeBytes));
    }
}
