package com.example.qrattendance.di

import com.example.qrattendance.data.remote.CloudFunctionsDataSource
import com.example.qrattendance.data.remote.FirebaseCloudFunctionsDataSource
import com.example.qrattendance.data.repository.AttendanceRepositoryImpl
import com.example.qrattendance.data.repository.AuthRepositoryImpl
import com.example.qrattendance.data.repository.SessionRepositoryImpl
import com.example.qrattendance.domain.repository.AttendanceRepository
import com.example.qrattendance.domain.repository.AuthRepository
import com.example.qrattendance.domain.repository.SessionRepository
import com.example.qrattendance.utils.ConnectivityNetworkMonitor
import com.example.qrattendance.utils.NetworkMonitor
import com.example.qrattendance.utils.QrCodeGenerator
import com.example.qrattendance.utils.ServerSyncedTimeProvider
import com.example.qrattendance.utils.TimeProvider
import com.example.qrattendance.utils.ZxingQrCodeGenerator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: AttendanceRepositoryImpl): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindCloudFunctionsDataSource(impl: FirebaseCloudFunctionsDataSource): CloudFunctionsDataSource
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UtilsModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: ServerSyncedTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindQrCodeGenerator(impl: ZxingQrCodeGenerator): QrCodeGenerator
}
