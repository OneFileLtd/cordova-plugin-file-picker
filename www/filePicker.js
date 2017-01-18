var exec = require('cordova/exec');
var FilePicker = function () { };

FilePicker.prototype.pickFile = function (config, success, error) {
	if (!config) {
		config = {};
	}
	if (!config.fileTypes) {
		config.fileTypes = ['*'];
	}
	exec(success, error, "FilePicker", "pickFile", [config]);
};

module.exports = new FilePicker();