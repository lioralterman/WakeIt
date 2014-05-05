/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: C:\\Users\\lior\\Desktop\\WakeIt\\WakeIt\\src\\com\\wakeit\\NotificationServiceInterface.aidl
 */
package com.wakeit;
public interface NotificationServiceInterface extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.wakeit.NotificationServiceInterface
{
private static final java.lang.String DESCRIPTOR = "com.wakeit.NotificationServiceInterface";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.wakeit.NotificationServiceInterface interface,
 * generating a proxy if needed.
 */
public static com.wakeit.NotificationServiceInterface asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.wakeit.NotificationServiceInterface))) {
return ((com.wakeit.NotificationServiceInterface)iin);
}
return new com.wakeit.NotificationServiceInterface.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_currentAlarmId:
{
data.enforceInterface(DESCRIPTOR);
long _result = this.currentAlarmId();
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_firingAlarmCount:
{
data.enforceInterface(DESCRIPTOR);
int _result = this.firingAlarmCount();
reply.writeNoException();
reply.writeInt(_result);
return true;
}
case TRANSACTION_volume:
{
data.enforceInterface(DESCRIPTOR);
float _result = this.volume();
reply.writeNoException();
reply.writeFloat(_result);
return true;
}
case TRANSACTION_acknowledgeCurrentNotification:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.acknowledgeCurrentNotification(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.wakeit.NotificationServiceInterface
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public long currentAlarmId() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_currentAlarmId, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public int firingAlarmCount() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
int _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_firingAlarmCount, _data, _reply, 0);
_reply.readException();
_result = _reply.readInt();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public float volume() throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
float _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
mRemote.transact(Stub.TRANSACTION_volume, _data, _reply, 0);
_reply.readException();
_result = _reply.readFloat();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public void acknowledgeCurrentNotification(int snoozeMinutes) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(snoozeMinutes);
mRemote.transact(Stub.TRANSACTION_acknowledgeCurrentNotification, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_currentAlarmId = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_firingAlarmCount = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_volume = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_acknowledgeCurrentNotification = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
}
public long currentAlarmId() throws android.os.RemoteException;
public int firingAlarmCount() throws android.os.RemoteException;
public float volume() throws android.os.RemoteException;
public void acknowledgeCurrentNotification(int snoozeMinutes) throws android.os.RemoteException;
}
