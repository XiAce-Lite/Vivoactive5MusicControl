import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

class MusicControlApp extends Application.AppBase {

    private var _volume as Number = 50;
    private var _phoneConnected as Boolean = false;
    private var _title as String = "";
    private var _artist as String = "";
    private var _playing as Boolean = false;
    private var _positionSec as Number = 0;
    private var _durationSec as Number = 180;
    private var _usePhoneMeta as Boolean = false;
    private var _skipHoldUntil as Number = 0;
    private var _metaGen as Number = -1;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        _title = WatchUi.loadResource(Rez.Strings.DemoTitle) as String;
        _artist = WatchUi.loadResource(Rez.Strings.DemoArtist) as String;
        if (Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        }
    }

    function onStop(state as Dictionary?) as Void {
        if (Communications has :registerForPhoneAppMessages) {
            Communications.registerForPhoneAppMessages(null);
        }
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data;
        if (data == null) {
            return;
        }
        if (!(data instanceof Dictionary)) {
            return;
        }
        var dict = data as Dictionary;
        _phoneConnected = true;

        var t = dict.get("t");
        if (t != null && t.equals("st")) {
            applyPhoneState(dict);
            WatchUi.requestUpdate();
        }
    }

    private function applyPhoneState(dict as Dictionary) as Void {
        _usePhoneMeta = true;
        var now = System.getTimer();
        var hold = now < _skipHoldUntil;

        var mg = dict.get("mg");
        if (mg != null) {
            var gen = mg as Number;
            if (gen != _metaGen) {
                _metaGen = gen;
                // New track meta from phone — clear skip hold and accept everything
                _skipHoldUntil = 0;
                hold = false;
            }
        }

        var p = dict.get("p");
        if (p != null) {
            _playing = (p as Number) != 0;
        }

        var ti = dict.get("ti");
        if (ti != null) {
            var newTitle = ti.toString();
            if (!newTitle.equals(_title)) {
                _title = newTitle;
                _positionSec = 0;
                if (!newTitle.equals("...")) {
                    _skipHoldUntil = 0;
                    hold = false;
                }
            }
        }
        var ar = dict.get("ar");
        if (ar != null) {
            var newArtist = ar.toString();
            if (!newArtist.equals(_artist)) {
                _artist = newArtist;
                if (ti == null) {
                    _positionSec = 0;
                }
            }
        }
        var pos = dict.get("pos");
        if (pos != null) {
            var newPos = pos as Number;
            if (!hold) {
                _positionSec = newPos;
            } else if (newPos < 3) {
                _positionSec = newPos;
            }
        }
        var dur = dict.get("dur");
        if (dur != null && (dur as Number) > 0) {
            _durationSec = dur as Number;
        }
        var vol = dict.get("vol");
        if (vol != null) {
            _volume = vol as Number;
        }
    }

    function markSkipPending() as Void {
        _title = "...";
        _positionSec = 0;
        _skipHoldUntil = System.getTimer() + 2500;
    }

    function isPhoneConnected() as Boolean {
        return _phoneConnected;
    }

    function setPhoneConnected(value as Boolean) as Void {
        _phoneConnected = value;
    }

    function usesPhoneMeta() as Boolean {
        return _usePhoneMeta;
    }

    function getTitle() as String {
        return _title;
    }

    function getArtist() as String {
        return _artist;
    }

    function isPlaying() as Boolean {
        return _playing;
    }

    function setPlaying(value as Boolean) as Void {
        _playing = value;
    }

    function getPositionSec() as Number {
        return _positionSec;
    }

    function setPositionSec(value as Number) as Void {
        _positionSec = value;
    }

    function getDurationSec() as Number {
        return _durationSec;
    }

    function getVolume() as Number {
        return _volume;
    }

    function setVolume(value as Number) as Void {
        if (value < 0) {
            value = 0;
        } else if (value > 100) {
            value = 100;
        }
        _volume = value;
        PhoneBridge.sendVolume(value);
    }

    function onBridgeHttp(responseCode as Number, data as Dictionary or String or Null) as Void {
        if (responseCode == 200) {
            _phoneConnected = true;
        }
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var view = new MusicControlView();
        return [view, new MusicControlDelegate(view)];
    }
}

function getApp() as MusicControlApp {
    return Application.getApp() as MusicControlApp;
}
