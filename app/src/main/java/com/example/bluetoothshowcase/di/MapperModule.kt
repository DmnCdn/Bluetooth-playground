package com.example.bluetoothshowcase.di

import android.content.Context
import com.example.bluetoothshowcase.mapper.BluetoothDeviceMapper
import dagger.Module
import dagger.Provides
import dagger.Reusable
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
class MapperModule {

    @Provides
    @Reusable
    fun provideDeviceMapper(
        @ApplicationContext context: Context
    ) = BluetoothDeviceMapper(context)

}