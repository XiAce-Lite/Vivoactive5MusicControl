import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Timer;
import Toybox.WatchUi;

//! Main music UI with standard-like icons and track progress ring.
class MusicControlView extends WatchUi.View {

    private const LINE_GAP = 1;
    private const DEMO_DURATION_SEC = 180;
    private const MARQUEE_GAP_PX = 40;
    private const MARQUEE_STEP_BASE = 3;
    private const MARQUEE_STEP_MAX = 5; // ~1.67x base; still readable

    private var _playing as Boolean = false;
    private var _positionSec as Number = 45;
    private var _marqueeOffset as Number = 0;
    private var _marqueeStep as Number = 3;
    private var _tickCount as Number = 0;
    private var _lastTitle as String = "";
    private var _playHoldUntil as Number = 0; // System.getTimer(); ignore phone play state until then
    private var _uiTimer as Timer.Timer?;

    var prevRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;
    var playRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;
    var nextRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;
    var volRect as Array<Number> = [0, 0, 0, 0] as Array<Number>;

    function initialize() {
        View.initialize();
    }

    function onShow() as Void {
        _uiTimer = new Timer.Timer();
        // 250ms: marquee + (every 4 ticks) track progress when playing
        _uiTimer.start(method(:onTimer), 250, true);
    }

    function onHide() as Void {
        if (_uiTimer != null) {
            _uiTimer.stop();
            _uiTimer = null;
        }
    }

    function onTimer() as Void {
        var app = getApp();
        var needDraw = false;
        if (app.usesPhoneMeta()) {
            var now = System.getTimer();
            var phonePos = app.getPositionSec();
            var wasPlaying = _playing;
            if (now >= _playHoldUntil) {
                _playing = app.isPlaying();
            }
            // Phone is sole progress source — no local +1 (that fought phone pushes)
            if (phonePos != _positionSec) {
                _positionSec = phonePos;
                needDraw = true;
            }
            if (wasPlaying != _playing) {
                needDraw = true;
            }
        } else if (_playing && (_tickCount % 4) == 0) {
            _positionSec += 1;
            if (_positionSec >= DEMO_DURATION_SEC) {
                _positionSec = 0;
            }
            needDraw = true;
        }
        _tickCount += 1;
        var titleLen = app.getTitle().length();
        if (titleLen > 14) {
            _marqueeOffset += _marqueeStep;
            needDraw = true;
        }
        if ((_tickCount % 4) == 0) {
            needDraw = true;
        }
        if (needDraw) {
            WatchUi.requestUpdate();
        }
    }

    function isPlaying() as Boolean {
        return _playing;
    }

    function togglePlay() as Void {
        _playing = !_playing;
        getApp().setPlaying(_playing);
        // Ignore stale phone "still playing" pushes for a short window
        _playHoldUntil = System.getTimer() + 3000;
        WatchUi.requestUpdate();
    }

    function onPrev() as Void {
        _positionSec = 0;
        _marqueeOffset = 0;
        getApp().markSkipPending();
        WatchUi.requestUpdate();
    }

    function onNext() as Void {
        _positionSec = 0;
        _marqueeOffset = 0;
        getApp().markSkipPending();
        WatchUi.requestUpdate();
    }

    function onUpdate(dc as Dc) as Void {
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        var cy = h / 2;
        var app = getApp();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        drawProgressRing(dc, cx, cy, w, h);

        var clock = System.getClockTime();
        var timeText = Lang.format("$1$:$2$", [
            clock.hour.format("%02d"),
            clock.min.format("%02d")
        ]);
        var clockFont = Graphics.FONT_SMALL;
        var clockY = (h * 0.09).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, clockY, clockFont, timeText, Graphics.TEXT_JUSTIFY_CENTER);

        // Compact meta so artist never overlaps transport buttons
        var titleFont = Graphics.FONT_TINY;
        var artistFont = Graphics.FONT_XTINY;
        var y = clockY + dc.getFontHeight(clockFont) + 2;
        var title = app.getTitle();
        if (!title.equals(_lastTitle)) {
            _lastTitle = title;
            _marqueeOffset = 0;
        }
        var titleAreaW = (w * 0.72).toNumber();
        drawMarqueeTitle(dc, title, titleFont, cx, y, titleAreaW);
        y += dc.getFontHeight(titleFont) + LINE_GAP;

        dc.setColor(Graphics.COLOR_LT_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, y, artistFont, app.getArtist(), Graphics.TEXT_JUSTIFY_CENTER);
        var metaBottom = y + dc.getFontHeight(artistFont);
        var metaLimit = (h * 0.33).toNumber();
        if (metaBottom > metaLimit) {
            metaBottom = metaLimit;
        }

        // Transport stays below meta; shrink play if vertical space is tight
        var skipSize = (w * 0.18).toNumber();
        var playSize = (w * 0.32).toNumber();
        var volSize = (w * 0.18).toNumber();
        var volY = (h * 0.80).toNumber() - volSize / 2;
        var transportTop = metaBottom + 8;
        var transportBottom = volY - 8;
        var avail = transportBottom - transportTop;
        if (playSize > avail && avail > 40) {
            playSize = avail;
        }
        var playY = transportTop + (avail - playSize) / 2;
        if (playY < transportTop) {
            playY = transportTop;
        }
        var midY = playY + playSize / 2;
        var gap = (w * 0.045).toNumber();
        var playX = cx - playSize / 2;
        var prevX = playX - gap - skipSize;
        var nextX = playX + playSize + gap;
        var skipY = midY - skipSize / 2;

        prevRect = [prevX, skipY, prevX + skipSize, skipY + skipSize] as Array<Number>;
        playRect = [playX, playY, playX + playSize, playY + playSize] as Array<Number>;
        nextRect = [nextX, skipY, nextX + skipSize, skipY + skipSize] as Array<Number>;

        drawSkipPrevIcon(dc, prevRect);
        if (_playing) {
            drawPauseIcon(dc, playRect);
        } else {
            drawPlayIcon(dc, playRect);
        }
        drawSkipNextIcon(dc, nextRect);

        var volX = (w * 0.30).toNumber() - volSize / 2;
        volRect = [volX, volY, volX + volSize, volY + volSize] as Array<Number>;
        drawVolumeIcon(dc, volRect);
    }

    private function drawMarqueeTitle(dc as Dc, title as String, font as FontDefinition, cx as Number, y as Number, maxW as Number) as Void {
        var tw = dc.getTextWidthInPixels(title, font);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        if (tw <= maxW) {
            _marqueeOffset = 0;
            _marqueeStep = MARQUEE_STEP_BASE;
            dc.drawText(cx, y, font, title, Graphics.TEXT_JUSTIFY_CENTER);
            return;
        }

        // Longer overflow → slightly faster, capped (~1.6x)
        var overflow = (tw - maxW).toFloat();
        var factor = 1.0 + (overflow / tw.toFloat()) * 0.8;
        if (factor > 1.6) {
            factor = 1.6;
        }
        _marqueeStep = (MARQUEE_STEP_BASE.toFloat() * factor + 0.5).toNumber();
        if (_marqueeStep < MARQUEE_STEP_BASE) {
            _marqueeStep = MARQUEE_STEP_BASE;
        }
        if (_marqueeStep > MARQUEE_STEP_MAX) {
            _marqueeStep = MARQUEE_STEP_MAX;
        }

        var fontH = dc.getFontHeight(font);
        var left = cx - maxW / 2;
        var cycle = tw + MARQUEE_GAP_PX;
        var offset = _marqueeOffset % cycle;
        dc.setClip(left, y, maxW, fontH + 2);
        dc.drawText(left - offset, y, font, title, Graphics.TEXT_JUSTIFY_LEFT);
        dc.drawText(left - offset + cycle, y, font, title, Graphics.TEXT_JUSTIFY_LEFT);
        dc.clearClip();
    }

    //! Full circle = track duration. Arc + knob = current position.
    private function drawProgressRing(dc as Dc, cx as Number, cy as Number, w as Number, h as Number) as Void {
        var radius = (w < h ? w : h) / 2 - 5;
        dc.setPenWidth(3);
        dc.setColor(0x333333, Graphics.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, radius);

        var progress = 0.0;
        var duration = DEMO_DURATION_SEC;
        if (getApp().usesPhoneMeta()) {
            duration = getApp().getDurationSec();
        }
        if (duration > 0) {
            progress = _positionSec.toFloat() / duration.toFloat();
        }
        if (progress < 0) {
            progress = 0.0;
        }
        if (progress > 1) {
            progress = 1.0;
        }
        if (progress <= 0) {
            dc.setPenWidth(1);
            return;
        }

        var startDeg = 90;
        var sweep = (360.0 * progress).toNumber();
        if (sweep < 1) {
            sweep = 1;
        }
        var endDeg = startDeg - sweep;
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(4);
        dc.drawArc(cx, cy, radius, Graphics.ARC_CLOCKWISE, startDeg, endDeg);

        var rad = endDeg * Math.PI / 180.0;
        var kx = cx + (radius * Math.cos(rad)).toNumber();
        var ky = cy - (radius * Math.sin(rad)).toNumber();
        dc.fillCircle(kx, ky, 5);
        dc.setPenWidth(1);
    }

    private function drawPlayIcon(dc as Dc, rect as Array<Number>) as Void {
        var cx = (rect[0] + rect[2]) / 2;
        var cy = (rect[1] + rect[3]) / 2;
        var s = ((rect[2] - rect[0]) * 0.28).toNumber();
        var x1 = cx - s / 2;
        var y1 = cy - s;
        var x2 = cx - s / 2;
        var y2 = cy + s;
        var x3 = cx + s;
        var y3 = cy;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.setPenWidth(3);
        dc.drawLine(x1, y1, x3, y3);
        dc.drawLine(x3, y3, x2, y2);
        dc.drawLine(x2, y2, x1, y1);
        dc.setPenWidth(1);
    }

    private function drawPauseIcon(dc as Dc, rect as Array<Number>) as Void {
        var cx = (rect[0] + rect[2]) / 2;
        var cy = (rect[1] + rect[3]) / 2;
        var barW = ((rect[2] - rect[0]) * 0.12).toNumber();
        var barH = ((rect[3] - rect[1]) * 0.42).toNumber();
        var gap = ((rect[2] - rect[0]) * 0.10).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(cx - gap / 2 - barW, cy - barH / 2, barW, barH, 2);
        dc.fillRoundedRectangle(cx + gap / 2, cy - barH / 2, barW, barH, 2);
    }

    private function drawSkipPrevIcon(dc as Dc, rect as Array<Number>) as Void {
        var cx = (rect[0] + rect[2]) / 2;
        var cy = (rect[1] + rect[3]) / 2;
        var s = ((rect[2] - rect[0]) * 0.22).toNumber();
        var barW = ((rect[2] - rect[0]) * 0.08).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillRectangle(cx - s - 2, cy - s, barW, s * 2);
        dc.fillPolygon([[cx - s + barW + 2, cy], [cx + s, cy - s], [cx + s, cy + s]]);
    }

    private function drawSkipNextIcon(dc as Dc, rect as Array<Number>) as Void {
        var cx = (rect[0] + rect[2]) / 2;
        var cy = (rect[1] + rect[3]) / 2;
        var s = ((rect[2] - rect[0]) * 0.22).toNumber();
        var barW = ((rect[2] - rect[0]) * 0.08).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillPolygon([[cx - s, cy - s], [cx - s, cy + s], [cx + s - barW - 2, cy]]);
        dc.fillRectangle(cx + s - barW, cy - s, barW, s * 2);
    }

    private function drawVolumeIcon(dc as Dc, rect as Array<Number>) as Void {
        var cx = (rect[0] + rect[2]) / 2;
        var cy = (rect[1] + rect[3]) / 2;
        var s = ((rect[2] - rect[0]) * 0.22).toNumber();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.fillPolygon([
            [cx - s, cy - s / 2],
            [cx - s / 3, cy - s / 2],
            [cx + s / 3, cy - s],
            [cx + s / 3, cy + s],
            [cx - s / 3, cy + s / 2],
            [cx - s, cy + s / 2]
        ]);
        dc.setPenWidth(2);
        dc.drawArc(cx + s / 3, cy, (s * 0.7).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, -40, 40);
        dc.drawArc(cx + s / 3, cy, (s * 1.15).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, -40, 40);
        dc.setPenWidth(1);
    }

    function hitTest(x as Number, y as Number) as Symbol? {
        if (inRect(x, y, prevRect)) {
            return :prev;
        }
        if (inRect(x, y, playRect)) {
            return :play;
        }
        if (inRect(x, y, nextRect)) {
            return :next;
        }
        if (inRect(x, y, volRect)) {
            return :volume;
        }
        return null;
    }

    private function inRect(x as Number, y as Number, rect as Array<Number>) as Boolean {
        return x >= rect[0] && x <= rect[2] && y >= rect[1] && y <= rect[3];
    }
}
