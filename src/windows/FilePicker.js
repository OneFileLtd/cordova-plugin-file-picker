(function () {
	"use strict";
	
	var FilePickerProxy = {
		pickFile: function (win, fail, args, env) {
			try {
				if (!args[0]) {
					fail("Missing options");
				}
				getFile(args[0].fileTypes)
					.then(moveToTempLocation)
					.done(success, error);
			} catch (e) {
				fail(e);
			}

			function success(file) {
				win(file);
			}

			function error(err) {
				fail(err);
			}
		}
	};
	
	function getFile(fileTypes) {
		var picker = Windows.Storage.Pickers.FileOpenPicker();
		picker.viewMode = Windows.Storage.Pickers.PickerViewMode.thumbnail;
		picker.suggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.picturesLibrary;
		picker.fileTypeFilter.replaceAll(fileTypes);
		return picker.pickSingleFileAsync()
			.then(function (file) {
				if (file) {
					return file;
				} else {
					return WinJS.Promise.wrapError('no file');
				}
			});
	}

	function moveToTempLocation(file) {
		var tempFolder = Windows.Storage.ApplicationData.current.temporaryFolder;
		return file.copyAsync(tempFolder)
			.then(function (newFile) {
				return newFile.getBasicPropertiesAsync.then(function (basicProps) {
					return {
						name: newFile.name,
						fullPath: newFile.path,
						localURL: 'ms-appdata:///temp/' + newFile.name,
						type: newFile.contentType,
						size: basicProps.size
					};
				});
			});
	}

	require("cordova/exec/proxy").add("FilePickerProxy", FilePickerProxy);
})();