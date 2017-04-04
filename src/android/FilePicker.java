package uk.co.onefile.nomadionic.filepicker;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import org.apache.cordova.*;
import org.apache.cordova.file.FileUtils;
import org.apache.cordova.file.LocalFilesystemURL;
import org.apache.cordova.mediacapture.FileHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FilePicker extends CordovaPlugin {
	public final static int PICK_PHOTO_CODE = 1046;
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
			onPickPhoto(currentWebView.getView());
		} catch (JSONException e) {
			e.printStackTrace();
			currentCallbackContext.error(e.getMessage());
		}
	}

	public void onPickPhoto(View view) {
		PackageManager packageManager = this.cordova.getActivity().getPackageManager();
		Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		intent.setType("image/* video/*");
		if (intent.resolveActivity(packageManager) != null) {
			this.cordova.startActivityForResult((CordovaPlugin) this, intent, PICK_PHOTO_CODE);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		final Intent dataInner = data;
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				if (dataInner != null) {
					Uri photoUri = dataInner.getData();
					JSONArray  result = new JSONArray();
					JSONObject file = createMediaFile(photoUri);
					result.put(file);
					currentCallbackContext.success(result);
				}
			}
		});
	}

	private JSONObject createMediaFile(Uri data) {
		File fp = currentWebView.getResourceApi().mapUriToFile(data);
		JSONObject obj = new JSONObject();

		Class webViewClass = currentWebView.getClass();
		PluginManager pm = null;
		try {
			Method gpm = webViewClass.getMethod("getPluginManager");
			pm = (PluginManager) gpm.invoke(currentWebView);
		} catch (NoSuchMethodException e) {
		} catch (IllegalAccessException e) {
		} catch (InvocationTargetException e) {
		}
		if (pm == null) {
			try {
				Field pmf = webViewClass.getField("pluginManager");
				pm = (PluginManager)pmf.get(currentWebView);
			} catch (NoSuchFieldException e) {
			} catch (IllegalAccessException e) {
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
		}
		return obj;
	}
}