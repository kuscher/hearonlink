# Services, receivers, tiles and widgets are referenced from the manifest; R8 keeps those.
# The hidden BluetoothDevice.createL2capSocket is looked up by name at runtime (framework class, not ours).

# Glance uses WorkManager, whose Room database is created by reflection (WorkDatabase_Impl).
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
