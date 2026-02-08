<h1>NEO Planner</h1>

<p>
NEO Planner is an Android application built with Jetpack Compose that calculates
the upcoming visibility of near-Earth objects (NEOs) from your location.  It
queries NASA’s NeoWS service to obtain orbital data, computes ephemerides using
JPL’s DE 442s ephemeris and presents you with the best observing windows for
each object.
</p>

<h2>Features</h2>
<ul>
  <li><strong>Compose UI with multiple tabs.</strong>  The top‑level screen provides
    Planner, Results, Pointing and Camera tabs.  The Planner tab collects
    input parameters; the Results tab lists planned NEO observations; Pointing
    and Camera tabs display pointing information and a camera overlay for the
    selected object.</li>
  <li><strong>Customizable planning parameters.</strong>  Users can specify how far
    ahead to search (hours), the sampling interval in minutes, a minimum
    altitude for visible objects, a twilight limit to define darkness and the
    maximum number of objects to return.  These parameters are exposed in the
    Planner tab and saved to persistent storage.</li>
  <li><strong>Ephemeris based visibility algorithm.</strong>  The core
    <code>VisibilityPlanner</code> class loads DE 442s ephemeris data and iterates
    through sample times up to the requested horizon.  For each sample it
    calculates the Sun’s altitude and the topocentric altitude/azimuth of every
    candidate NEO.  It marks time ranges when the Sun is below the specified
    twilight limit and the NEO’s altitude is above the user‑defined minimum.
    For each object the planner finds the peak altitude within its visibility
    window, computes the azimuth and cardinal direction and records the time
    range.</li>
  <li><strong>Data retrieval.</strong>  The app fetches NEO candidate data from NASA’s
    NeoWS API via <code>NeoWsClient</code> and approximates the observer’s
    geographic location from their IP address using <code>IpGeoClient</code>.  An
    API key for NeoWS can be saved in the settings; if missing the app prompts
    the user to provide one.</li>
  <li><strong>Results &amp; pointing.</strong>  Planned results are sorted by peak
    altitude and displayed in the Results tab.  Selecting a result opens
    the Pointing tab with azimuth and altitude guidance and allows switching to
    a Camera view.  The app also calculates Moon and planet positions using the
    JPL Horizons API and uses the Moon as a calibration target for device
    orientation.
  </li>
</ul>

<h2>Getting&nbsp;started</h2>
<p>
Clone the repository and open it in Android Studio.  Before running the app,
obtain an API key from NASA’s <a href="https://api.nasa.gov/">api.nasa.gov</a>
and save it via the settings dialog when prompted.  When the app starts,
enter your planning parameters in the Planner tab and tap “Plan.”  After the
computation finishes, review the results in the Results tab and use the
Pointing or Camera views to observe selected objects.
</p>
