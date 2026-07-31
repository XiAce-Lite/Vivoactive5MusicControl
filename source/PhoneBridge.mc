import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;

//! Watch → phone via HTTP only (127.0.0.1 on the phone).
//! Do not also BLE-transmit: dual delivery caused double skip / lag.
class PhoneBridge {
    static function sendCommand(cmd as String) as Void {
        httpGet(Lang.format("http://127.0.0.1:17865/cmd?c=$1$", [cmd]));
    }

    static function sendVolume(value as Number) as Void {
        httpGet(Lang.format("http://127.0.0.1:17865/cmd?c=vol&v=$1$", [value]));
    }

    private static function httpGet(url as String) as Void {
        if (!(Communications has :makeWebRequest)) {
            return;
        }
        var options = {
            :method => Communications.HTTP_REQUEST_METHOD_GET,
            :headers => {
                "Content-Type" => Communications.REQUEST_CONTENT_TYPE_URL_ENCODED
            },
            :responseType => Communications.HTTP_RESPONSE_CONTENT_TYPE_TEXT_PLAIN
        };
        Communications.makeWebRequest(url, {}, options, getApp().method(:onBridgeHttp));
    }
}
