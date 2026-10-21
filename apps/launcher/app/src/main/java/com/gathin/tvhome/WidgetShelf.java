package com.gathin.tvhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.utilities.Blend;
import com.google.android.material.color.utilities.Hct;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Home's widget shelf: four cards in the open space between the header and the dock, one over each
 * dock column, so they line up with the apps below them.
 * <ul>
 *   <li>Weather ({@link Weather}): OK steps from now (temperature and range) to how it feels (humidity, wind) to tomorrow (high, low, chance of rain).</li>
 *   <li>This TV: CPU load (every few seconds, {@link CpuMeter}), memory and storage in use, to read only. (Android 14 lets an app
 *       end only its own background processes, so a launcher cannot free other apps' memory itself.)</li>
 *   <li>Air quality: the US AQI and PM2.5 at the weather's place ({@link AirQuality}), its band in the
 *       band's colour harmonised with the scheme; OK switches between now and tomorrow's worst hour.</li>
 *   <li>Lunar calendar: today's lunar date, the year's can chi and the next holiday; OK steps through the
 *       holidays after it.</li>
 * </ul>
 * The owner picks which widgets show and in what order: an empty column holds an Add widget card (OK
 * lists the widgets not shown), and holding OK on a widget offers Move left, Move right and Remove.
 * The list is kept in the launcher's settings ({@link #ORDER_KEY}), and so is each widget's own
 * settings (Settings on its menu): the place and unit of the weather (the air quality shares the
 * place), whether the calendar counts down to every holiday or only Tết, and whether This TV shows CPU.
 * It scrolls away with the grid when focus goes below the dock. Nothing runs while Home is hidden, and
 * nothing on it animates except focus: the box this runs on is slow.
 */
@SuppressLint("RestrictedApi")   // material-color-utilities' Blend and Hct, as in Palette
final class WidgetShelf {

    static final String PREF_KEY = "widgets";
    /** The widgets on the shelf, left to right, comma separated. */
    static final String ORDER_KEY = "widget_order";
    static final String WEATHER = "weather", SYSTEM = "system", AIR = "air", CALENDAR = "calendar";
    private static final String[] ALL = {WEATHER, SYSTEM, AIR, CALENDAR};
    private static final int MAX_CARDS = 4;

    /** What the shelf needs from Home: its menus, the storage page, and a say when the cards change. */
    interface Host {
        void showMenu(View anchor, List<MenuItem> items);

        /** The cards on the shelf changed: the header's Down target may have moved. */
        void slotsChanged();

        /** A widget was picked up or put down in Edit Home. */
        void carryChanged();

        /** Starts Edit Home (Move on a widget's menu); false when it cannot start. */
        boolean startEditing();
    }

    static final class MenuItem {
        final int icon;
        final String title;
        final Runnable action;
        /** A setting's current value, shown at the row's end; null for an action. */
        final String value;

        MenuItem(int icon, String title, Runnable action) {
            this(icon, title, action, null);
        }

        MenuItem(int icon, String title, Runnable action, String value) {
            this.icon = icon;
            this.title = title;
            this.action = action;
            this.value = value;
        }
    }
    /** A failed weather fetch is not retried sooner than this. */
    private static final long WEATHER_RETRY_MS = 5 * 60_000L;

    private final Activity activity;
    private final View shelf;
    private final WidgetCard[] cards;
    private final WidgetCard addCard;
    private final TextView addLabel;
    private final ImageView addIcon;
    /** The widgets shown, left to right (ids from {@link #ALL}). */
    private final List<String> order = new ArrayList<>();
    /** The cards on the shelf now, left to right: the widgets that fit, then Add widget if a column is free. */
    private final List<WidgetCard> slots = new ArrayList<>();
    private RecyclerView lastGrid;
    /** Cards move when the list changes: added ones grow in, removed ones shrink out, the rest slide. */
    private final boolean motion;
    /** The focus indicator sliding between the cards, as between app tiles. */
    private final ShelfHighlight highlight;
    /** Edit Home is on: the indicator takes the edit colour and OK picks a widget up. */
    private boolean editing;
    /** The widget being carried in Edit Home, or null. */
    private String carried;
    private static final float ENTER_SCALE = 0.85f;
    private int lastColumns = MAX_CARDS, lastDockTop;
    private int cellOrigin, cellPitch, cellWidth, tilePitch;
    /** The dock's first tile and how many tiles it has, for moving focus between cards and apps. */
    private int dockLeft, dockTiles = 1;
    private final TextView weatherPlace, weatherTemp, weatherDetail;
    private final ShapeBadge weatherIcon;
    private final TextView ramValue, storageValue;
    private final M3Progress ramBar, storageBar;
    private final ImageView ramIcon, storageIcon;
    private final TextView airLabel, airValue, airDetail;
    private final ShapeBadge airBadge;
    private final TextView lunarLabel, lunarDate, holiday;
    private final TextView ramName, storageName, cpuName, cpuValue;
    private final M3Progress cpuBar;
    private final ImageView cpuIcon;
    private final CpuMeter cpu;
    /** How often the CPU meter reads while Home shows: often enough to see a load come and go. */
    private static final long CPU_MS = 3000;
    private final Runnable cpuTick = new Runnable() {
        @Override
        public void run() {
            int busy = cpu.sample();
            if (busy != CpuMeter.NOT_YET) lastCpu = busy;
            if (systemPage == SystemPages.USAGE && showCpu() || systemPage == SystemPages.CHIP) paintSystem();
            main.postDelayed(this, CPU_MS);
        }
    };
    private int lastCpu = CpuMeter.NOT_YET;
    /** How far the header reaches over the grid's frame, from the last align. */
    private int headerUnderlap;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final float density;
    /** The weather_city in config.xml: the place until the owner picks one on the widget. */
    private final String configCity;
    private final SharedPreferences prefs;
    private static final String CITY_KEY = "weather_city", UNIT_KEY = "weather_unit",
            TET_ONLY_KEY = "calendar_tet_only", CPU_KEY = "system_cpu";
    /** The page each card opens on when Home shows, from its settings. */
    private static final String WEATHER_PAGE_KEY = "weather_page", AIR_PAGE_KEY = "air_page",
            SYSTEM_PAGE_KEY = "system_page";
    private static final int[] WEATHER_PAGE_NAMES = {R.string.page_now, R.string.page_details, R.string.page_tomorrow};
    private static final int[] AIR_PAGE_NAMES = {R.string.page_today, R.string.page_tomorrow};
    /** The places offered, as geocoder queries ("name|country"); "" is wherever the IP says. */
    private static final String[] CITY_QUERIES = {"", "Hanoi|VN", "Ho Chi Minh City|VN", "Da Nang|VN", "Haiphong|VN",
            "Can Tho|VN", "Hue|VN", "Nha Trang|VN", "Da Lat|VN"};
    private static final String[] CITY_NAMES = {null, "Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng",
            "Cần Thơ", "Huế", "Nha Trang", "Đà Lạt"};
    private final Host host;
    private Palette palette = Palette.currentOrBaseline();
    private boolean enabled, started;

    private Weather.Report report;
    private boolean fetching;
    private long lastFailure;

    private AirQuality.Report air;
    private boolean airFetching, airTomorrow;
    private long airFailure;

    private int holidayStep;
    /** What the weather card shows, stepped by OK: now, the details of now, tomorrow. */
    private int weatherPage;
    private static final int WEATHER_PAGES = 3;
    /** What the system card shows, stepped by OK (SystemPages), in its three meter rows. */
    private int systemPage;
    private final SystemPages systemPages;
    private final ImageView[] rowIcons;
    private final TextView[] rowNames, rowValues;
    private final M3Progress[] rowBars;
    private IntConsumer downFromCard;

    WidgetShelf(Activity activity, View shelf, boolean animations, boolean reducedMotion, boolean expressiveMotion, Host host) {
        this.activity = activity;
        this.shelf = shelf;
        this.host = host;
        motion = animations && !reducedMotion;
        highlight = ShelfHighlight.attach((ViewGroup) shelf, animations, reducedMotion, expressiveMotion);
        density = activity.getResources().getDisplayMetrics().density;
        configCity = activity.getString(R.string.weather_city).trim();
        prefs = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        cpu = new CpuMeter(activity);
        cards = new WidgetCard[]{shelf.findViewById(R.id.widgetWeather), shelf.findViewById(R.id.widgetSystem),
                shelf.findViewById(R.id.widgetAir), shelf.findViewById(R.id.widgetCalendar)};
        weatherPlace = shelf.findViewById(R.id.weatherPlace);
        weatherTemp = shelf.findViewById(R.id.weatherTemp);
        weatherDetail = shelf.findViewById(R.id.weatherDetail);
        weatherIcon = shelf.findViewById(R.id.weatherIcon);
        ramValue = shelf.findViewById(R.id.ramValue);
        storageValue = shelf.findViewById(R.id.storageValue);
        ramBar = shelf.findViewById(R.id.ramBar);
        storageBar = shelf.findViewById(R.id.storageBar);
        ramIcon = shelf.findViewById(R.id.ramIcon);
        storageIcon = shelf.findViewById(R.id.storageIcon);
        airLabel = shelf.findViewById(R.id.airLabel);
        airValue = shelf.findViewById(R.id.airValue);
        airDetail = shelf.findViewById(R.id.airDetail);
        airBadge = shelf.findViewById(R.id.airBadge);
        airBadge.setIcon(activity.getDrawable(R.drawable.ic_air));
        lunarLabel = shelf.findViewById(R.id.lunarLabel);
        lunarDate = shelf.findViewById(R.id.lunarDate);
        holiday = shelf.findViewById(R.id.holiday);
        cpuName = shelf.findViewById(R.id.cpuName);
        cpuValue = shelf.findViewById(R.id.cpuValue);
        cpuBar = shelf.findViewById(R.id.cpuBar);
        cpuIcon = shelf.findViewById(R.id.cpuIcon);
        ramName = shelf.findViewById(R.id.ramName);
        storageName = shelf.findViewById(R.id.storageName);
        systemPages = new SystemPages(activity);
        rowIcons = new ImageView[]{cpuIcon, ramIcon, storageIcon};
        rowNames = new TextView[]{cpuName, ramName, storageName};
        rowValues = new TextView[]{cpuValue, ramValue, storageValue};
        rowBars = new M3Progress[]{cpuBar, ramBar, storageBar};
        for (TextView line : new TextView[]{weatherPlace, weatherDetail, airLabel, airDetail, lunarLabel, holiday}) {
            TextFit.attach(line);
        }
        for (int i = 0; i < rowNames.length; i++) {
            TextFit.attachRow(rowNames[i], rowValues[i], (View) rowNames[i].getParent());
        }
        addCard = shelf.findViewById(R.id.widgetAdd);
        addLabel = shelf.findViewById(R.id.widgetAddLabel);
        addIcon = shelf.findViewById(R.id.widgetAddIcon);
        addCard.setGhost(true);
        loadOrder();


        for (WidgetCard card : allCards()) {
            card.setOnKeyListener((v, code, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int column = slots.indexOf(v);
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && downFromCard != null) {
                    downFromCard.accept(dockColumnUnderCard(Math.max(0, column)));
                    return true;
                }
                // The row's ends: focus must not wrap off the shelf sideways.
                return code == KeyEvent.KEYCODE_DPAD_LEFT && column == 0
                        || code == KeyEvent.KEYCODE_DPAD_RIGHT && column == slots.size() - 1;
            });
        }
        for (int i = 0; i < ALL.length; i++) {
            String id = ALL[i];
            cards[i].setOnLongClickListener(v -> {
                showEditMenu(id);
                return true;
            });
        }
        addCard.setOnClickListener(v -> showAddMenu());
        for (WidgetCard card : allCards()) card.setMotion(motion);
        cards[0].setOnClickListener(v -> {
            weatherPage = (weatherPage + 1) % WEATHER_PAGES;
            cards[0].turn(this::paintWeather);
            refreshWeather(false);
        });
        cards[1].setOnClickListener(v -> {
            systemPage = (systemPage + 1) % SystemPages.COUNT;
            cards[1].turn(this::paintSystem);
        });
        cards[2].setOnClickListener(v -> {
            airTomorrow = !airTomorrow;
            cards[2].turn(this::paintAir);
        });
        cards[3].setOnClickListener(v -> {
            holidayStep++;
            cards[3].turn(this::paintCalendar);
        });
    }

    /** Down from the card over dock column i: the activity focuses the app below it. */
    void setOnDown(IntConsumer focusDock) {
        downFromCard = focusDock;
    }

    boolean enabled() {
        return enabled;
    }

    /** Shows or hides the shelf (Launcher settings, "widgets"); a hidden shelf reads nothing. */
    void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        shelf.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) stop();
    }

    /** Focus moves up from dock column i: onto the card over it. */
    boolean focusFromDock(int column) {
        int n = slots.size();
        if (!enabled || n == 0 || shelf.getAlpha() < 0.5f) return false;
        return slots.get(Math.max(0, Math.min(cardOverDockColumn(column), n - 1))).requestFocus();
    }

    /** The card header buttons should go down to: the last one, under them. */
    int cardUnderHeaderButtons() {
        return slots.isEmpty() ? addCard.getId() : slots.get(slots.size() - 1).getId();
    }

    private WidgetCard[] allCards() {
        return new WidgetCard[]{cards[0], cards[1], cards[2], cards[3], addCard};
    }

    private WidgetCard card(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(id)) return cards[i];
        return null;
    }

    private boolean has(String id) {
        return order.contains(id);
    }

    // ---- choosing widgets ------------------------------------------------------------------------

    private void loadOrder() {
        order.clear();
        String saved = activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(ORDER_KEY, String.join(",", ALL));
        for (String id : saved.split(",")) {
            id = id.trim();
            if (id.equals("now_playing")) id = AIR;   // the widget air quality replaced, in its place
            if (card(id) != null && !order.contains(id)) order.add(id);
        }
    }

    /**
     * Saves a new list and shows it, focus on focusId's card, or on the card now at the same column.
     * removing is the widget leaving, if one is: it shrinks and fades out first (emphasized accelerate,
     * as M3 exits do), then the others slide into place on a default spatial spring.
     */
    private void setOrder(List<String> next, String focusId, int fallbackColumn, String removing) {
        WidgetCard leaving = removing == null ? null : card(removing);
        if (!motion || leaving == null) {
            applyOrder(next, focusId, fallbackColumn);
            return;
        }
        leaving.animate().cancel();
        leaving.animate().alpha(0f).scaleX(ENTER_SCALE).scaleY(ENTER_SCALE)
                .setInterpolator(Motion.emphasizedAccelerate()).setDuration(Motion.EMPHASIZED_ACCELERATE_MS)
                .withEndAction(() -> {
                    leaving.setAlpha(1f);
                    leaving.setScaleX(1f);
                    leaving.setScaleY(1f);
                    applyOrder(next, focusId, fallbackColumn);
                }).start();
    }

    private void applyOrder(List<String> next, String focusId, int fallbackColumn) {
        Map<WidgetCard, Integer> before = new HashMap<>();
        for (WidgetCard c : slots) before.put(c, c.getLeft());
        order.clear();
        order.addAll(next);
        activity.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(ORDER_KEY, String.join(",", order)).apply();
        if (started) {
            // Start or stop what the added or removed widget reads.
            stop();
            start();
        }
        if (lastGrid != null) align(lastGrid, lastColumns, lastDockTop);
        host.slotsChanged();
        if (motion) {
            // After the new layout, before it is drawn: start each card from where it was.
            shelf.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    shelf.getViewTreeObserver().removeOnPreDrawListener(this);
                    for (WidgetCard c : slots) {
                        Integer from = before.get(c);
                        if (from == null) {
                            c.setAlpha(0f);
                            c.setScaleX(ENTER_SCALE);
                            c.setScaleY(ENTER_SCALE);
                            Motion.DEFAULT_SPATIAL.drive(c.animate().alpha(1f).scaleX(1f).scaleY(1f)).start();
                        } else if (from != c.getLeft()) {
                            c.setTranslationX(from - c.getLeft());
                            Motion.DEFAULT_SPATIAL.drive(c.animate().translationX(0f)).start();
                        }
                    }
                    return true;
                }
            });
        }
        shelf.post(() -> {
            WidgetCard target = focusId != null ? card(focusId) : null;
            if (target == null || !slots.contains(target)) {
                target = slots.isEmpty() ? null : slots.get(Math.max(0, Math.min(fallbackColumn, slots.size() - 1)));
            }
            if (target != null) {
                target.requestFocus();
                highlight.follow(target);   // a card that kept focus but moved fires no focus change
            }
        });
    }

    private void showAddMenu() {
        List<MenuItem> items = new ArrayList<>();
        for (String id : ALL) {
            if (has(id)) continue;
            items.add(new MenuItem(icon(id), activity.getString(name(id)), () -> {
                List<String> next = new ArrayList<>(order);
                next.add(id);
                setOrder(next, id, next.size() - 1, null);
            }));
        }
        if (!items.isEmpty()) host.showMenu(addCard, items);
    }

    private void showEditMenu(String id) {
        int at = order.indexOf(id);
        if (at < 0) return;
        List<MenuItem> items = new ArrayList<>();
        // Move works as on an app's menu: Edit Home starts with this widget picked up.
        if (Math.min(order.size(), slotsLimit()) > 1) {
            items.add(new MenuItem(R.drawable.ic_move, activity.getString(R.string.tile_move), () -> {
                if (!host.startEditing()) return;
                WidgetCard card = card(id);
                if (card != null) card.requestFocus();
                carried = id;
                highlight.setLifted(true);
                host.carryChanged();
            }));
        }
        items.add(new MenuItem(R.drawable.ic_settings_outline, activity.getString(R.string.widget_settings),
                () -> showSettingsMenu(id)));
        items.add(new MenuItem(R.drawable.ic_remove, activity.getString(R.string.widget_remove), () -> {
            List<String> next = new ArrayList<>(order);
            next.remove(id);
            setOrder(next, null, at, id);
        }));
        host.showMenu(card(id), items);
    }

    private void move(String id, int by) {
        List<String> next = new ArrayList<>(order);
        int at = next.indexOf(id);
        if (at < 0 || at + by < 0 || at + by >= Math.min(next.size(), slotsLimit())) return;
        Collections.swap(next, at, at + by);
        setOrder(next, id, at + by, null);
    }

    // ---- Edit Home -------------------------------------------------------------------------------

    void setEditing(boolean on) {
        editing = on;
        if (!on) drop();
        highlight.setColor(on ? palette.tertiary : palette.primary);
    }

    /** The carried widget's name, or null. */
    String carriedLabel() {
        return carried == null ? null : activity.getString(name(carried));
    }

    /**
     * A key in Edit Home with focus on the shelf. OK picks the focused widget up; while it is carried,
     * Left and Right move it along the shelf, Up and Down do nothing, and OK or Back put it down. False
     * for keys left to the activity (Back with nothing carried ends the mode; OK on Add widget opens it).
     */
    boolean handleEditKey(KeyEvent event, View focused) {
        if (!editing || !(focused instanceof WidgetCard) || focused.getParent() != shelf) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean back = code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE;
        if (carried != null) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) move(carried, code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (ok || back) {
                if (!down) drop();
                return true;
            }
            return false;
        }
        String id = idOf(focused);
        if (ok && id != null) {
            if (!down && event.getRepeatCount() == 0) {
                carried = id;
                highlight.setLifted(true);
                host.carryChanged();
            }
            return true;
        }
        return false;
    }

    private void drop() {
        if (carried == null) return;
        carried = null;
        highlight.setLifted(false);
        host.carryChanged();
    }

    private String idOf(View card) {
        for (int i = 0; i < ALL.length; i++) if (cards[i] == card) return ALL[i];
        return null;
    }

    // ---- per-widget settings ---------------------------------------------------------------------

    private String city() {
        return prefs.getString(CITY_KEY, configCity);
    }

    private boolean fahrenheit() {
        return "f".equals(prefs.getString(UNIT_KEY, "c"));
    }

    private boolean tetOnly() {
        return prefs.getBoolean(TET_ONLY_KEY, false);
    }

    private boolean showCpu() {
        return prefs.getBoolean(CPU_KEY, true);
    }

    /** The place's name as the menu shows it. */
    private String cityName(String query) {
        for (int i = 0; i < CITY_QUERIES.length; i++) {
            if (CITY_QUERIES[i].equals(query)) return CITY_NAMES[i] != null ? CITY_NAMES[i] : activity.getString(R.string.widget_city_auto);
        }
        return query.contains("|") ? query.substring(0, query.indexOf('|')) : query;
    }

    /**
     * A widget's settings, matching what the card shows: each row its icon, its name and, at its end, the current
     * value; OK on one lists its choices. Every card that steps through pages chooses the one it opens on.
     */
    private void showSettingsMenu(String id) {
        WidgetCard anchor = card(id);
        List<MenuItem> items = new ArrayList<>();
        switch (id) {
            case WEATHER:
                items.add(setting(R.drawable.ic_location, R.string.setting_location, cityName(city()),
                        () -> showCityMenu(anchor)));
                items.add(setting(R.drawable.ic_thermostat, R.string.setting_unit, fahrenheit() ? "°F" : "°C",
                        () -> showChoices(anchor, new String[]{"°C", "°F"}, fahrenheit() ? 1 : 0, i -> {
                            prefs.edit().putString(UNIT_KEY, i == 1 ? "f" : "c").apply();
                            paintWeather();
                        })));
                items.add(opensOn(anchor, WEATHER_PAGE_KEY, WEATHER_PAGE_NAMES, i -> {
                    weatherPage = i;
                    paintWeather();
                }));
                break;
            case AIR:
                items.add(setting(R.drawable.ic_location, R.string.setting_location_shared, cityName(city()),
                        () -> showCityMenu(anchor)));
                items.add(opensOn(anchor, AIR_PAGE_KEY, AIR_PAGE_NAMES, i -> {
                    airTomorrow = i == 1;
                    paintAir();
                }));
                break;
            case CALENDAR:
                String[] countdown = {activity.getString(R.string.countdown_next),
                        activity.getString(R.string.countdown_tet)};
                items.add(setting(R.drawable.ic_timer, R.string.setting_countdown, countdown[tetOnly() ? 1 : 0],
                        () -> showChoices(anchor, countdown, tetOnly() ? 1 : 0, i -> {
                            prefs.edit().putBoolean(TET_ONLY_KEY, i == 1).apply();
                            holidayStep = 0;
                            paintCalendar();
                        })));
                break;
            default:
                items.add(opensOn(anchor, SYSTEM_PAGE_KEY, SystemPages.NAMES, i -> {
                    systemPage = i;
                    paintSystem();
                }));
                String[] shown = {activity.getString(R.string.option_shown),
                        activity.getString(R.string.option_hidden)};
                items.add(setting(R.drawable.ic_memory, R.string.setting_cpu, shown[showCpu() ? 0 : 1],
                        () -> showChoices(anchor, shown, showCpu() ? 0 : 1, i -> {
                            prefs.edit().putBoolean(CPU_KEY, i == 0).apply();
                            if (i == 0 && lastCpu == CpuMeter.UNAVAILABLE) {
                                // The TV hides the figures from apps: ask it to allow reading them through adb.
                                cpu.askToAllow();
                                lastCpu = CpuMeter.NOT_YET;
                            }
                            paintSystem();
                        })));
                break;
        }
        host.showMenu(anchor, items);
    }

    private MenuItem setting(int icon, int name, String value, Runnable choose) {
        return new MenuItem(icon, activity.getString(name), choose, value);
    }

    /** The page a card opens on when Home shows; picking one also shows it now. */
    private MenuItem opensOn(View anchor, String key, int[] pageNames, IntConsumer show) {
        String[] names = new String[pageNames.length];
        for (int i = 0; i < names.length; i++) names[i] = activity.getString(pageNames[i]);
        int current = Math.min(prefs.getInt(key, 0), names.length - 1);
        return setting(R.drawable.ic_swap_vert, R.string.setting_opens, names[current],
                () -> showChoices(anchor, names, current, i -> {
                    prefs.edit().putInt(key, i).apply();
                    show.accept(i);
                }));
    }

    /** The places: Automatic (the IP's) and the main cities. Weather and air quality both refresh for it. */
    private void showCityMenu(View anchor) {
        String current = city();
        String[] names = new String[CITY_QUERIES.length];
        int selected = -1;
        for (int i = 0; i < CITY_QUERIES.length; i++) {
            names[i] = cityName(CITY_QUERIES[i]);
            if (CITY_QUERIES[i].equals(current)) selected = i;
        }
        showChoices(anchor, names, selected, i -> {
            prefs.edit().putString(CITY_KEY, CITY_QUERIES[i]).apply();
            if (has(WEATHER)) refreshWeather(true);
            if (has(AIR)) refreshAir(true);
        });
    }

    /** A menu of choices, the current one marked, like a radio group. */
    private void showChoices(View anchor, String[] labels, int selected, IntConsumer choose) {
        List<MenuItem> items = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            items.add(new MenuItem(i == selected ? R.drawable.ic_radio_on : R.drawable.ic_radio_off, labels[i],
                    () -> choose.accept(index)));
        }
        host.showMenu(anchor, items);
    }

    /**
     * How many cards fit: one per dock column, at most four. The shelf spans the full row of columns even
     * when the dock holds fewer apps: DockDecoration centres a short dock, so the full row is centred on it.
     */
    private boolean measureDock(RecyclerView grid, int columns) {
        int itemCount = grid.getAdapter() == null ? 0 : grid.getAdapter().getItemCount();
        int lastColumn = Math.max(0, Math.min(columns, itemCount) - 1);
        Rect first = tileBounds(grid, 0);
        Rect second = lastColumn > 0 ? tileBounds(grid, 1) : null;
        Rect last = tileBounds(grid, lastColumn);
        if (first == null || last == null || (lastColumn > 0 && second == null)) return false;

        int tileWidth = first.width();
        int pitch = second != null ? Math.abs(second.left - first.left)
                : (grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight()) / Math.max(1, columns);
        int gap = pitch - tileWidth;
        int tilesLeft = Math.min(first.left, last.left);
        int centre = (tilesLeft + Math.max(first.right, last.right)) / 2;
        int rowWidth = (columns - 1) * pitch + tileWidth;
        int cells = slotsLimit();
        cellOrigin = centre - rowWidth / 2;
        cellWidth = (rowWidth - gap * (cells - 1)) / cells;
        cellPitch = cellWidth + gap;
        tilePitch = pitch;
        dockLeft = tilesLeft;
        dockTiles = lastColumn + 1;
        return true;
    }

    private Rect tileBounds(RecyclerView grid, int position) {
        RecyclerView.ViewHolder holder = grid.findViewHolderForAdapterPosition(position);
        View tile = holder == null ? null : holder.itemView.findViewById(R.id.card);
        if (tile == null || tile.getWidth() == 0) return null;

        Rect bounds = new Rect(0, 0, tile.getWidth(), tile.getHeight());
        ((ViewGroup) shelf.getParent()).offsetDescendantRectToMyCoords(tile, bounds);
        return bounds;
    }

    private int dockColumnUnderCard(int card) {
        if (tilePitch <= 0) return card;
        int centre = cellOrigin + card * cellPitch + cellWidth / 2;
        return Math.max(0, Math.min(dockTiles - 1, Math.floorDiv(centre - dockLeft, tilePitch)));
    }

    private int cardOverDockColumn(int column) {
        if (cellPitch <= 0) return column;
        int centre = dockLeft + column * tilePitch + tilePitch / 2;
        return Math.max(0, Math.floorDiv(centre - cellOrigin, cellPitch));
    }

    private int slotsLimit() {
        return Math.min(MAX_CARDS, lastColumns);
    }

    private static int name(String id) {
        switch (id) {
            case WEATHER: return R.string.weather;
            case SYSTEM: return R.string.widget_system;
            case AIR: return R.string.air_quality;
            default: return R.string.lunar_calendar;
        }
    }

    private static int icon(String id) {
        switch (id) {
            case WEATHER: return R.drawable.ic_w_partly_day;
            case SYSTEM: return R.drawable.ic_memory;
            case AIR: return R.drawable.ic_air;
            default: return R.drawable.ic_calendar;
        }
    }

    // ---- lifecycle -------------------------------------------------------------------------------

    void start() {
        if (!enabled || started) return;
        started = true;
        weatherPage = prefs.getInt(WEATHER_PAGE_KEY, 0) % WEATHER_PAGES;
        airTomorrow = prefs.getInt(AIR_PAGE_KEY, 0) == 1;
        systemPage = prefs.getInt(SYSTEM_PAGE_KEY, 0) % SystemPages.COUNT;
        if (has(CALENDAR)) paintCalendar();
        if (has(SYSTEM)) {
            // The baseline: the first figure comes with the first tick, unless the TV hides every source.
            if (cpu.sample() == CpuMeter.UNAVAILABLE) lastCpu = CpuMeter.UNAVAILABLE;
            paintSystem();
            main.postDelayed(cpuTick, CPU_MS);
        }
        if (has(WEATHER)) refreshWeather(false);
        if (has(AIR)) refreshAir(false);
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(cpuTick);
        cpu.stop();
    }

    /** Once a minute while Home shows. */
    void tick() {
        if (!started) return;
        if (has(CALENDAR)) paintCalendar();
        if (has(SYSTEM)) paintSystem();
        if (has(WEATHER)) refreshWeather(false);
        if (has(AIR)) refreshAir(false);
    }

    // ---- placement -------------------------------------------------------------------------------

    /**
     * Puts the shown cards over the dock's columns, left to right (each card's visible part exactly over
     * its tile, its focus room around it), and centres the shelf in the space above the dock. dockTop is
     * the dock's top in the shelf's parent, which also holds the grid. At most one card per column and
     * four in all; a free column gets the Add widget card.
     */
    void align(RecyclerView grid, int columns, int dockTop) {
        lastGrid = grid;
        lastColumns = columns;
        lastDockTop = dockTop;
        if (!enabled) return;
        slots.clear();
        for (String id : order) if (slots.size() < slotsLimit()) slots.add(card(id));
        if (slots.size() < slotsLimit() && order.size() < ALL.length) slots.add(addCard);
        for (WidgetCard c : allCards()) {
            int visibility = slots.contains(c) ? View.VISIBLE : View.GONE;
            if (c.getVisibility() != visibility) c.setVisibility(visibility);
        }
        if (!measureDock(grid, columns)) return;

        int inset = Math.round(WidgetCard.REACH_DP * density);
        boolean moved = false;
        for (int index = 0; index < slots.size(); index++) {
            WidgetCard card = slots.get(index);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            int left = cellOrigin + index * cellPitch - inset - shelf.getLeft();
            int width = cellWidth + 2 * inset;
            if (lp.leftMargin != left || lp.width != width) {
                lp.leftMargin = left;
                lp.width = width;
                card.setLayoutParams(lp);
                moved = true;
            }
        }
        // A focused card that moved (the dock re-centred under it) fires no focus change: follow it once laid out.
        if (moved) shelf.post(() -> {
            for (WidgetCard card : slots) highlight.follow(card);
        });
        // Halfway between the header, which floats over the top of the grid, and the dock.
        headerUnderlap = grid instanceof HomeGrid ? ((HomeGrid) grid).underlap() : 0;
        ViewGroup.MarginLayoutParams slp = (ViewGroup.MarginLayoutParams) shelf.getLayoutParams();
        int top = headerUnderlap + Math.max(0, (dockTop - headerUnderlap - slp.height) / 2);
        if (slp.topMargin != top) {
            slp.topMargin = top;
            shelf.setLayoutParams(slp);
        }
    }

    void onGridScrolled(RecyclerView grid) {
        if (!enabled) return;

        int offset = grid.computeVerticalScrollOffset();
        shelf.setTranslationY(-offset);

        // Whole until it reaches the header, then fading out as it slides under the header's scrim.
        ViewGroup.MarginLayoutParams layout = (ViewGroup.MarginLayoutParams) shelf.getLayoutParams();
        float underHeader = offset - (layout.topMargin - headerUnderlap);
        float visibility = Math.max(0f, Math.min(1f, 1f - underHeader / Math.max(1f, shelf.getHeight())));
        shelf.setAlpha(visibility);
        shelf.setVisibility(visibility == 0f ? View.INVISIBLE : View.VISIBLE);
    }

    // ---- colour ----------------------------------------------------------------------------------

    void setPalette(Palette p) {
        palette = p;
        for (WidgetCard c : allCards()) c.setPalette(p);
        highlight.setColor(editing ? p.tertiary : p.primary);
        addLabel.setTextColor(p.onSurfaceVariant);
        addIcon.setImageTintList(ColorStateList.valueOf(p.onSurfaceVariant));
        for (TextView t : new TextView[]{weatherPlace, airLabel, lunarLabel}) t.setTextColor(p.onSurfaceVariant);
        for (TextView t : new TextView[]{weatherTemp, lunarDate, airValue, cpuName, ramName, storageName}) t.setTextColor(p.onSurface);
        for (TextView t : new TextView[]{weatherDetail, airDetail, holiday, cpuValue, ramValue, storageValue}) t.setTextColor(p.onSurfaceVariant);
        cpuIcon.setImageTintList(ColorStateList.valueOf(p.primary));
        cpuBar.setPalette(p);
        ramIcon.setImageTintList(ColorStateList.valueOf(p.primary));
        storageIcon.setImageTintList(ColorStateList.valueOf(p.primary));
        ramBar.setPalette(p);
        storageBar.setPalette(p);
        weatherIcon.setPalette(p);
        paintAir();
    }

    // ---- weather ---------------------------------------------------------------------------------

    private void refreshWeather(boolean force) {
        if (report == null) report = Weather.cached(activity);
        paintWeather();
        long now = System.currentTimeMillis();
        if (fetching) return;
        if (!force && report != null && !report.stale(now)) return;
        if (!force && now - lastFailure < WEATHER_RETRY_MS) return;
        fetching = true;
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            Weather.Report fresh;
            try {
                fresh = Weather.fetch(app, city());
            } catch (Exception e) {
                fresh = null;
            }
            Weather.Report result = fresh;
            main.post(() -> {
                fetching = false;
                if (result != null) report = result;
                else lastFailure = System.currentTimeMillis();
                paintWeather();
            });
        }, "weather").start();
    }

    private void paintWeather() {
        if (report == null) {
            weatherPlace.setText(R.string.weather);
            weatherTemp.setText("–");
            weatherDetail.setText(lastFailure > 0 ? activity.getString(R.string.weather_offline) : "");
            weatherIcon.setIcon(activity.getDrawable(R.drawable.ic_w_cloud));
            return;
        }
        String place = report.place.isEmpty() ? activity.getString(R.string.weather) : report.place;
        Weather.Sky sky = Weather.sky(weatherPage == 2 ? report.tomorrowCode : report.code);
        if (weatherPage == 1) {
            weatherPlace.setText(R.string.weather_feels);
            weatherTemp.setText(degrees(report.feels) + "°");
            weatherDetail.setText(activity.getString(fahrenheit() ? R.string.weather_details_mph : R.string.weather_details,
                    report.humidity, (int) Math.round(fahrenheit() ? report.wind / 1.609 : report.wind)));
        } else if (weatherPage == 2) {
            weatherPlace.setText(activity.getString(R.string.weather_tomorrow, place));
            weatherTemp.setText(degrees(report.tomorrowHigh) + "°");
            weatherDetail.setText(activity.getString(R.string.weather_tomorrow_detail, degrees(report.tomorrowLow),
                    report.tomorrowRain));
        } else {
            weatherPlace.setText(place);
            weatherTemp.setText(degrees(report.temp) + "°");
            weatherDetail.setText(activity.getString(R.string.weather_range, activity.getString(Weather.label(sky)),
                    degrees(report.high), degrees(report.low)));
        }
        weatherIcon.setIcon(activity.getDrawable(Weather.icon(sky, weatherPage == 2 || report.day)));
    }

    /** A Celsius reading in the unit the owner chose, rounded. */
    private int degrees(double celsius) {
        return (int) Math.round(fahrenheit() ? celsius * 9 / 5 + 32 : celsius);
    }

    // ---- this TV ---------------------------------------------------------------------------------

    /**
     * The system card's page in its three meter rows. A page of fewer lines leaves the top row out (the CPU
     * one, as when it is hidden); a line without a share drops its bar, so the gaps between rows stay even
     * and a page of names and values sits in the middle of the card rather than high in it.
     */
    private void paintSystem() {
        List<SystemPages.Line> lines = systemPages.lines(systemPage, lastCpu, showCpu());
        int offset = rowIcons.length - lines.size();
        int top = offset > 0 ? View.GONE : View.VISIBLE;
        for (int id : new int[]{R.id.cpuRow, R.id.cpuBar, R.id.cpuGap}) shelf.findViewById(id).setVisibility(top);
        for (int i = 0; i < lines.size(); i++) {
            SystemPages.Line line = lines.get(i);
            int row = i + offset;
            rowIcons[row].setImageResource(line.icon);
            rowNames[row].setText(line.name);
            rowValues[row].setText(line.value);
            boolean bar = line.fill != SystemPages.Line.NO_BAR;
            rowBars[row].setVisibility(bar ? View.VISIBLE : View.GONE);
            if (bar) rowBars[row].setProgress(line.fill);
        }
    }

    // ---- air quality -----------------------------------------------------------------------------

    private void refreshAir(boolean force) {
        if (air == null) air = AirQuality.cached(activity);
        paintAir();
        long now = System.currentTimeMillis();
        if (airFetching) return;
        if (!force && air != null && !air.stale(now)) return;
        if (!force && now - airFailure < WEATHER_RETRY_MS) return;
        airFetching = true;
        Context app = activity.getApplicationContext();
        new Thread(() -> {
            AirQuality.Report fresh;
            try {
                fresh = AirQuality.fetch(app, city());
            } catch (Exception e) {
                fresh = null;
            }
            AirQuality.Report result = fresh;
            main.post(() -> {
                airFetching = false;
                if (result != null) air = result;
                else airFailure = System.currentTimeMillis();
                paintAir();
            });
        }, "air").start();
    }

    /**
     * Now, or tomorrow's worst hour. The badge takes the band's colour harmonised towards the scheme's
     * primary and set to the scheme's tone (light in dark mode, deep in light mode), so it reads as
     * the band at a glance and still belongs to the palette.
     */
    private void paintAir() {
        boolean tomorrow = airTomorrow && air != null && air.tomorrowMax >= 0;
        String title = activity.getString(R.string.air_quality);
        airLabel.setText(tomorrow ? activity.getString(R.string.air_tomorrow, title) : title);
        if (air == null) {
            airValue.setText("–");
            airDetail.setText(airFailure > 0 ? activity.getString(R.string.air_offline) : "");
            airBadge.setColors(palette.surfaceContainerHighest, palette.onSurfaceVariant);
            return;
        }
        int aqi = tomorrow ? air.tomorrowMax : air.aqi;
        AirQuality.Level level = AirQuality.level(aqi);
        String band = activity.getString(AirQuality.label(level));
        airValue.setText(String.valueOf(aqi));
        airDetail.setText(tomorrow ? activity.getString(R.string.air_detail_tomorrow, band)
                : activity.getString(R.string.air_detail, band, air.pm25));
        Hct hct = Hct.fromInt(Blend.harmonize(AirQuality.color(level), palette.primary));
        boolean dark = palette.dark;
        int fill = Hct.from(hct.getHue(), Math.max(hct.getChroma(), 36), dark ? 80 : 40).toInt();
        int on = Hct.from(hct.getHue(), Math.min(hct.getChroma(), 16), dark ? 20 : 100).toInt();
        airBadge.setColors(fill, on);
    }

    // ---- lunar calendar --------------------------------------------------------------------------

    private void paintCalendar() {
        LocalDate today = LocalDate.now();
        LunarCalendar.Date l = LunarCalendar.toLunar(today);
        lunarLabel.setText(activity.getString(R.string.lunar_year, LunarCalendar.canChi(l.year)));
        String date = l.day + "/" + l.month;
        lunarDate.setText(l.leap ? activity.getString(R.string.lunar_leap, date) : date);
        List<LunarCalendar.Upcoming> upcoming = LunarCalendar.upcoming(today);
        if (tetOnly()) upcoming.removeIf(u -> u.holiday != LunarCalendar.Holiday.TET);
        if (upcoming.isEmpty()) {
            holiday.setText("");
            return;
        }
        LunarCalendar.Upcoming u = upcoming.get(Math.floorMod(holidayStep, upcoming.size()));
        String name = activity.getString(holidayName(u.holiday));
        holiday.setText(u.days == 0 ? activity.getString(R.string.holiday_today, name)
                : u.days == 1 ? activity.getString(R.string.holiday_tomorrow, name)
                : activity.getString(R.string.holiday_in_days, name, (int) u.days));
    }

    private static int holidayName(LunarCalendar.Holiday h) {
        switch (h) {
            case NEW_YEAR: return R.string.holiday_new_year;
            case TET: return R.string.holiday_tet;
            case HUNG_KINGS: return R.string.holiday_hung_kings;
            case REUNIFICATION: return R.string.holiday_reunification;
            case LABOUR: return R.string.holiday_labour;
            case MID_AUTUMN: return R.string.holiday_mid_autumn;
            case NATIONAL_DAY: return R.string.holiday_national_day;
            default: return R.string.holiday_christmas;
        }
    }
}
