package uk.co.onefile.nomadionic.filepicker;

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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import android.content.Context;
import android.content.ContextWrapper;

public class FilePicker extends CordovaPlugin {
	public final static int PICK_FROM_GALLERY_CODE = 1046;
	private static final int REQUEST_WRITE_PERMISSION = 786;
	private static final int STATUS_ERROR = 0;
	private static final int STATUS_SUCCESSFUL = 1;
	private static final String VIDEO_3GPP = "video/3gpp";
	private static final String VIDEO_MP4 = "video/mp4";
	private static final String IMAGE_JPEG = "image/jpeg";
	public CallbackContext currentCallbackContext;
	public CordovaWebView currentWebView;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		currentWebView = webView;
		super.initialize(cordova, webView);
	}

	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
		Log.i("FilePicker", action);
		if (action.equals("pickFile")) {
			final JSONObject config = args.getJSONObject(0);
			cordova.getThreadPool().execute(new Runnable() {
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
			requestPermission();
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		}
	}

	private void requestPermission() {
		boolean needExternalStoragePermission = !PermissionHelper.hasPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (needExternalStoragePermission) {
				Log.i("FilePicker", "requestPermission - onPickPhoto - permission required");
				PermissionHelper.requestPermission(this, REQUEST_WRITE_PERMISSION, Manifest.permission.READ_EXTERNAL_STORAGE);
			} else {
				Log.i("FilePicker", "requestPermission - onPickPhoto - permission required, and granted");
				onPickPhoto(currentWebView.getView());
			}
		} else {
			Log.i("FilePicker", "requestPermission - onPickPhoto - permission not required");
			onPickPhoto(currentWebView.getView());
		}
	}

	public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
		Log.i("onRequestPermissionsRes", "requestCode:" + requestCode);
		Log.i("onRequestPermissionsRes", permissions.toString());
		Log.i("onRequestPermissionsRes", grantResults.toString());
		if (requestCode == REQUEST_WRITE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			Log.i("FilePicker", "onRequestPermissionsResult - onPickPhoto");
			onPickPhoto(currentWebView.getView());
		} else
		{
			currentCallbackContext.error("Permission denied, please check your permissions");
		}
	}

	public void onPickPhoto(View view) {
		PackageManager packageManager = this.cordova.getActivity().getPackageManager();
		// Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		// intent.setType("image/* video/*");
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			Log.i("onPickPhoto", "<19");
			Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
			intent.setType("image/* video/*");
			if (intent.resolveActivity(packageManager) != null) {
				Log.i("FilePicker", "onPickPhoto");
				this.cordova.startActivityForResult((CordovaPlugin) this, intent, PICK_FROM_GALLERY_CODE);
			}
		} else {
			Log.i("onPickPhoto", ">=19");
			Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
			intent.setType("*/*");
			intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
			if (intent.resolveActivity(packageManager) != null) {
				Log.i("FilePicker", "onPickPhoto");
				this.cordova.startActivityForResult((CordovaPlugin) this, intent, PICK_FROM_GALLERY_CODE);
			}
		}

	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		final Intent dataInner = data;
		Log.i("onActivityResult", "build-version:" + Build.VERSION.SDK_INT);
		Log.i("onActivityResult", data.getDataString());
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				if (dataInner != null) {
					Log.i("onActivityResult", dataInner.getDataString());
					Uri photoUri = dataInner.getData();
					JSONArray result = new JSONArray();
					JSONObject file;
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
						Log.i("onActivityResult", "<19");
						file = createMediaFile(photoUri);
					} else {
						Log.i("onActivityResult", ">=19");
						file = createMediaFile(photoUri);
					}
					if(file == null) {
						Log.i("onActivityResult", "no file data");
						currentCallbackContext.error("no file data available, please check permissions");
						return;
					}
					result.put(file);
					currentCallbackContext.success(result);
				}
			}
		});
	}

	private JSONObject createMediaFile(Uri data) {
		File fp = new File(getPath(cordova.getActivity().getApplicationContext(), data)); //currentWebView.getResourceApi().mapUriToFile(data);
		JSONObject obj = new JSONObject();

		Class webViewClass = currentWebView.getClass();
		PluginManager pm = null;
		try {
			Method gpm = webViewClass.getMethod("getPluginManager");
			pm = (PluginManager) gpm.invoke(currentWebView);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		} catch (IllegalAccessException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		} catch (InvocationTargetException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		}
		if (pm == null) {
			try {
				Field pmf = webViewClass.getField("pluginManager");
				pm = (PluginManager)pmf.get(currentWebView);
			} catch (NoSuchFieldException e) {
				e.printStackTrace();
				currentCallbackContext.error(e.getMessage());
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				currentCallbackContext.error(e.getMessage());
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
				obj.put("type", FileHelper.getMimeType(Uri.fromFile(fp), cordova));
			}

			obj.put("lastModifiedDate", fp.lastModified());
			obj.put("size", fp.length());
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		} catch (NullPointerException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		}
		return obj;
	}

	public static String getPath(Context context, Uri uri) {
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

		// This code below is in the camera plugin, FileHelper.java..
		// TODO: Need to use that version if available.
		// DocumentProvider
		if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
			// ExternalStorageProvider
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
				final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
				return getDataColumn(context, contentUri, null, null);
			}
			// MediaProvider
			else
			if (isMediaDocument(uri)) {
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
				final String[] selectionArgs = new String[] {split[1]};
				return getDataColumn(context, contentUri, selection, selectionArgs);
			}
		}
		// MediaStore (and general)
		else if ("content".equalsIgnoreCase(uri.getScheme())) {
			// Return the remote address
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();
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
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}
}