package uk.co.onefile.nomadionic.filepicker;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import org.apache.cordova.*;
import org.apache.cordova.file.FileUtils;
import org.apache.cordova.file.LocalFilesystemURL;
import org.apache.cordova.mediacapture.FileHelper;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLEncoder;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import android.content.Context;
import android.content.ContextWrapper;
import android.widget.Toast;

public class FilePicker extends CordovaPlugin {
	public final static int PICK_FROM_GALLERY_CODE = 1046;
	private static final int REQUEST_WRITE_PERMISSION = 786;
	private static final int REQUEST_READ_PERMISSION = 787;
	private static final int STATUS_ERROR = 0;
	private static final int STATUS_SUCCESSFUL = 1;
	private static final String VIDEO_3GPP = "video/3gpp";
	private static final String VIDEO_MP4 = "video/mp4";
	private static final String IMAGE_JPEG = "image/jpeg";
	private static boolean importedFile;
	private static boolean permissionDenied;
	public CallbackContext currentCallbackContext;
	public CordovaWebView currentWebView;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		currentWebView = webView;
		importedFile = false;
		permissionDenied = false;
		super.initialize(cordova, webView);
	}

	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
		if (action.equals("pickFile")) {
			final JSONObject config = args.getJSONObject(0);
			this.cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					picker(config, callbackContext);
				}
			});
			return true;
		}
		return false;
	}

	private void picker(JSONObject config, CallbackContext callbackContext) {
		currentCallbackContext = callbackContext;
		try {
			Log.i("FilePicker", config.toString(2));
			checkPermissions();
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		}
	}

	private void checkPermissions() {
		boolean needWriteExternalStoragePermission = !PermissionHelper.hasPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
		boolean needReadExternalStoragePermission = !PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
		permissionDenied = false;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (needWriteExternalStoragePermission) {
				PermissionHelper.requestPermission(this, REQUEST_WRITE_PERMISSION, Manifest.permission.WRITE_EXTERNAL_STORAGE);
			} else if (needReadExternalStoragePermission) {
				PermissionHelper.requestPermission(this, REQUEST_READ_PERMISSION, Manifest.permission.READ_EXTERNAL_STORAGE);
			} else {
				onPickPhoto(currentWebView.getView());
			}
		} else {
			onPickPhoto(currentWebView.getView());
		}
	}

	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		switch (requestCode) {
			case REQUEST_READ_PERMISSION: {
				if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					permissionDenied = true;
					currentCallbackContext.error("permission denied");
				}
				return;
			}
			case REQUEST_WRITE_PERMISSION: {

				if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					permissionDenied = true;
					currentCallbackContext.error("permission denied");
				}
				return;
			}
		}
		checkPermissions();
	}

	public void onPickPhoto(View view) {
		PackageManager packageManager = this.cordova.getActivity().getPackageManager();
		Intent intent;
		importedFile = true;
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			intent.setType("image/* video/* audio/*");
		} else {
			intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*", "audio/*", "text/*", "application/*"});
		}
		if (intent.resolveActivity(packageManager) != null) {
			this.cordova.startActivityForResult((CordovaPlugin) this, intent, PICK_FROM_GALLERY_CODE);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == PICK_FROM_GALLERY_CODE) {
			if (resultCode == cordova.getActivity().RESULT_OK) {
				if (data == null) {
					currentCallbackContext.error("no data");
					return;
				}
				this.cordova.getThreadPool().execute(new Runnable() {
					public void run() {
						Uri photoUri = data.getData();
						Log.i("onActivityResult", "execute-inthread:" + photoUri.toString());
						JSONArray result = new JSONArray();
						JSONObject file;
						file = createMediaFile(photoUri);
						if (file == null) {
							currentCallbackContext.error("no file data available, please check permissions");
							return;
						}
						result.put(file);
						currentCallbackContext.success(result);
						return;
					}
				});
			} else if (resultCode == Activity.RESULT_CANCELED) {
				currentCallbackContext.error("user cancelled");
			} else if (resultCode == Activity.RESULT_FIRST_USER) {
			}
		}
	}

	private JSONObject createMediaFile(Uri data) {
		try {
			if(data == null) {
				return null;
			}
			boolean needReadExternalStoragePermission = !PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
			String filePath = getPath(this.cordova.getActivity().getApplicationContext(), data);
			Log.i("createMediaFile", filePath);
			File fp = new File(filePath);
			if (!fp.canRead()) {
				Log.i("createMediaFile", "file can not read!!!");
			}
			if (!fp.exists()) {
				Log.i("createMediaFile", "file not found!!!");
				return null;
			}
			JSONObject obj = new JSONObject();

			Class webViewClass = currentWebView.getClass();
			PluginManager pm = null;
			try {
				Method gpm = webViewClass.getMethod("getPluginManager");
				pm = (PluginManager) gpm.invoke(currentWebView);
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
				return null;
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				return null;
			} catch (InvocationTargetException e) {
				e.printStackTrace();
				return null;
			}
			if (pm == null) {
				try {
					Field pmf = webViewClass.getField("pluginManager");
					pm = (PluginManager) pmf.get(currentWebView);
				} catch (NoSuchFieldException e) {
					e.printStackTrace();
					return null;
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					return null;
				}
			}
			FileUtils filePlugin = (FileUtils) pm.getPlugin("File");
			LocalFilesystemURL url = filePlugin.filesystemURLforLocalPath(fp.getAbsolutePath());

			try {
				obj.put("name", fp.getName());
				obj.put("fullPath", Uri.fromFile(fp));
				if (url != null) {
					obj.put("localURL", url.toString());
				}
				if (fp.getAbsoluteFile().toString().endsWith(".3gp") || fp.getAbsoluteFile().toString().endsWith(".3gpp")) {
					obj.put("type", VIDEO_3GPP);
				} else {
					obj.put("type", FileHelper.getMimeType(Uri.fromFile(fp), this.cordova));
				}

				obj.put("lastModifiedDate", fp.lastModified());
				obj.put("size", fp.length());
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			} catch (NullPointerException e) {
				e.printStackTrace();
				return null;
			}
			if (obj.isNull("name")) {
			} else {
				try {
					Log.i("createMediaFile", obj.toString(2));
				} catch (JSONException e) {
					e.printStackTrace();
					return null;
				}
			}
			return obj;
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String getPath(final Context context, final Uri uri) {

		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/" + split[1];
				}

				// TODO handle non-primary volumes
			}
			// DownloadsProvider
			else if (isDownloadsDocument(uri)) {

				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
					Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] {
					split[1]
				};
				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {

			// Return the remote address
			if (isGooglePhotosUri(uri)) {
				return uri.getLastPathSegment();
			}
			return getDataColumn(context, uri, null, null);
		}
		// File
		else if ("file".equalsIgnoreCase(uri.getScheme())) {
			return uri.getPath();
		}
		return null;
	}

	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is DownloadsProvider.
	 */
	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is MediaProvider.
	 */
	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri.getAuthority());
	}

	/**
	 * @param uri The Uri to check.
	 * @return Whether the Uri authority is Google Photos.
	 */
	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri.getAuthority());
	}

	public static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = { column };
		try {
			cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				String returnData = cursor.getString(index);
				return returnData;
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
		return null;
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onPause(boolean multitasking) {
		super.onPause(multitasking);
	}

	@Override
	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		if(permissionDenied)
			return;
		if(!importedFile)
			checkPermissions();
	}

	@Override
	public void onRestoreStateForActivityResult(Bundle state, CallbackContext callbackContext) {
		super.onRestoreStateForActivityResult(state, callbackContext);
	}

	@Override
	public Bundle onSaveInstanceState() {
		super.onSaveInstanceState();
		return null;
	}

	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
	}

	@Override
	public Object onMessage(String id, Object data) {
		super.onMessage(id, data);
		return null;
	}
}