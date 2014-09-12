package com.bukanir.android.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bukanir.android.R;
import com.bukanir.android.activities.PlayerActivity;
import com.bukanir.android.entities.Summary;
import com.bukanir.android.scrapers.Podnapisi;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.bukanir.android.Torrent2Http;
import com.bukanir.android.entities.Movie;
import com.bukanir.android.entities.Subtitle;
import com.bukanir.android.entities.TorrentFile;
import com.bukanir.android.entities.TorrentStatus;
import com.bukanir.android.scrapers.Titlovi;
import com.bukanir.android.services.Torrent2HttpService;
import com.bukanir.android.utils.Utils;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class MovieFragment extends Fragment implements View.OnClickListener {

    public static final String TAG = "MovieFragment";

    Movie movie;
    Summary summary;
    Button buttonWatch;
    Subtitle subtitle;
    String subtitlePath;
    String subtitleLanguage;
    DisplayImageOptions options;
    ProgressBar progressBar;
    TextView downloadingText;
    ImageLoader imageLoader = ImageLoader.getInstance();
    Torrent2HttpTask torrent2HttpTask;
    SubtitleTask subtitleTask;

    public static MovieFragment newInstance(Movie movie, Summary summary) {
        MovieFragment fragment = new MovieFragment();
        Bundle args = new Bundle();
        args.putSerializable("movie", movie);
        args.putSerializable("summary", summary);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        if(savedInstanceState != null) {
            movie = (Movie) savedInstanceState.getSerializable("movie");
            summary = (Summary) savedInstanceState.getSerializable("summary");
        } else {
            movie = (Movie) getArguments().getSerializable("movie");
            summary = (Summary) getArguments().getSerializable("summary");
        }

        getActivity().setProgressBarIndeterminateVisibility(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        subtitleLanguage = prefs.getString("sub_lang", "2");

        options = new DisplayImageOptions.Builder()
                .showImageOnLoading(R.drawable.ic_stub)
                .showImageForEmptyUri(R.drawable.ic_empty)
                .showImageOnFail(R.drawable.ic_error)
                .cacheOnDisc(true)
                .considerExifParams(true)
                .displayer(new SimpleBitmapDisplayer())
                .build();

        View rootView = inflater.inflate(R.layout.fragment_movie, container, false);

        progressBar = (ProgressBar) rootView.findViewById(R.id.progressBar);
        progressBar.setVisibility(View.INVISIBLE);

        downloadingText = (TextView) rootView.findViewById(R.id.downloading);
        downloadingText.setVisibility(View.INVISIBLE);

        buttonWatch = (Button) rootView.findViewById(R.id.watch);
        buttonWatch.setEnabled(true);
        buttonWatch.setOnClickListener(this);

        ImageView image = (ImageView) rootView.findViewById(R.id.image);

        setMovieText(rootView);

        if(!imageLoader.isInited()) {
            File imagesDir = new File(getActivity().getExternalCacheDir().toString() + File.separator + "images");
            imagesDir.mkdirs();
            ImageLoaderConfiguration config = new
                    ImageLoaderConfiguration.Builder(getActivity().getApplicationContext())
                    .discCache(new UnlimitedDiscCache(imagesDir))
                    .defaultDisplayImageOptions(DisplayImageOptions.createSimple())
                    .build();
            imageLoader.init(config);
        }
        imageLoader.displayImage(movie.posterLarge, image, options);

        return rootView;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if(subtitleTask != null) {
            if(subtitleTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                subtitleTask.cancel(true);
            }
        }
        if(torrent2HttpTask != null) {
            if(torrent2HttpTask.getStatus().equals(AsyncTask.Status.RUNNING)) {
                torrent2HttpTask.cancel(true);
                progressBar.setVisibility(View.INVISIBLE);
                downloadingText.setVisibility(View.INVISIBLE);
            }
        }
        if(Utils.isServiceRunning(getActivity())) {
            Intent intent = new Intent(getActivity(), Torrent2HttpService.class);
            getActivity().stopService(intent);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        outState.putSerializable("movie", movie);
        outState.putSerializable("summary", summary);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        buttonWatch.setEnabled(true);
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick");
        if(view.getId() == R.id.watch) {
            if(Utils.isStorageAvailable()) {
                if(Utils.isFreeSpaceAvailable(getActivity(), movie)) {
                    view.setEnabled(false);
                    startMovie();
                } else {
                    Toast.makeText(getActivity(), getString(R.string.freespace_not_available), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getActivity(), getString(R.string.storage_not_available), Toast.LENGTH_LONG).show();
            }
        }
    }

    public void setMovieText(View rootView) {
        TextView title = (TextView) rootView.findViewById(R.id.title);
        TextView info = (TextView) rootView.findViewById(R.id.info);
        TextView info2 = (TextView) rootView.findViewById(R.id.info2);
        TextView cast = (TextView) rootView.findViewById(R.id.cast);
        TextView tagline = (TextView) rootView.findViewById(R.id.tagline);
        TextView overview = (TextView) rootView.findViewById(R.id.overview);

        if(movie == null || summary == null) {
            return;
        }

        title.setText(Utils.toTitleCase(movie.title));
        cast.setText(summary.cast);
        overview.setText(summary.overview);

        if(!summary.tagline.isEmpty()) {
            tagline.setText(summary.tagline);
        } else {
            tagline.setVisibility(View.GONE);
        }

        String year = "";
        if(!movie.year.isEmpty()) {
            year = String.format("(%s)  ", movie.year);
        }
        String rating = "";
        if(!summary.rating.equals("0.0")) {
            rating = String.format("%s / 10  ", summary.rating);
        }
        String runtime = "";
        if(!summary.runtime.equals("0")) {
            runtime = String.format("%s min  ", summary.runtime);
        }
        String size = "";
        if(!movie.sizeHuman.isEmpty()) {
            size = movie.sizeHuman;
        }
        if(!runtime.isEmpty() && !size.isEmpty()) {
            runtime += "/ ";
        }

        info.setText(year + rating);
        info2.setText(runtime + size);
    }

    public void startMovie() {
        Log.d(TAG, "startMovie");
        Intent intent = new Intent(getActivity(), Torrent2HttpService.class);
        intent.putExtra("magnet", movie.magnetLink);
        getActivity().startService(intent);

        subtitleTask = new SubtitleTask();
        torrent2HttpTask = new Torrent2HttpTask();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            subtitleTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, movie);
            torrent2HttpTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            subtitleTask.execute(movie);
            torrent2HttpTask.execute();
        }
    }

    private class Torrent2HttpTask extends AsyncTask<Void, Integer, TorrentFile> {

        protected void onPreExecute() {
            super.onPreExecute();
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setMax(100);
            downloadingText.setVisibility(View.VISIBLE);
            getActivity().setProgressBarIndeterminateVisibility(true);
        }

        protected TorrentFile doInBackground(Void... params) {
            Torrent2Http t2h = new Torrent2Http();
            boolean startup = t2h.waitStartup();
            if(!startup) {
                return null;
            }

            boolean ready = false;
            while(!ready) {
                TorrentStatus status = t2h.getStatus();
                if(Integer.parseInt(status.state) >= 3 && !ready) {
                    float progress = Float.parseFloat(status.progress) * 10000;
                    publishProgress(
                            (int) progress,
                            Integer.parseInt(status.state),
                            (int) Float.parseFloat(status.download_rate),
                            (int) Float.parseFloat(status.upload_rate),
                            Integer.parseInt(status.num_seeds),
                            Integer.parseInt(status.num_peers)
                    );
                    if(progress >= 100) {
                        ready = true;
                        break;
                    }
                } else {
                    publishProgress(
                            0,
                            Integer.parseInt(status.state)
                    );
                }

                if(isCancelled()) {
                    break;
                }

                try {
                    Thread.sleep(t2h.T2H_POLL);
                } catch(InterruptedException e) {
                }
            }

            return t2h.getLargestFile();
        }

        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress[0]);
            progressBar.setProgress(progress[0]);
            int state = progress[1];
            if(state == 0) {
                downloadingText.setText(getString(R.string.queued));
            } else if(state == 1) {
                downloadingText.setText(getString(R.string.checking));
            } else if(state == 2) {
                downloadingText.setText(getString(R.string.downloading_metadata));
            } else if(state == 3 && progress[0] == 0) {
                downloadingText.setText(getString(R.string.downloading));
            } else if(state >= 3) {
                String status = String.format(
                        "D:%dk U:%dk S:%d P:%d",
                        progress[2], progress[3], progress[4], progress[5]);
                downloadingText.setText(status);
            }
        }

        protected void onPostExecute(TorrentFile torrentFile) {
            buttonWatch.setEnabled(true);
            progressBar.setVisibility(View.INVISIBLE);
            downloadingText.setText("");
            downloadingText.setVisibility(View.INVISIBLE);
            getActivity().setProgressBarIndeterminateVisibility(false);

            if(torrentFile != null) {
                Log.d(TAG, torrentFile.toString());
                Intent intent = new Intent(getActivity(), PlayerActivity.class);
                intent.putExtra("sub", subtitlePath);
                intent.putExtra("file", torrentFile.name);
                intent.putExtra("subtitle", subtitle);
                startActivity(intent);
            }
        }

    }

    private class SubtitleTask extends AsyncTask<Movie, Void, String> {

        String cacheDir;

        protected void onPreExecute() {
            cacheDir = getActivity().getExternalCacheDir().toString() + File.separator + "movie";
        }

        protected String doInBackground(Movie... params) {
            Movie m = params[0];

            Podnapisi podnapisi = new Podnapisi();
            ArrayList<ArrayList<String>> subtitles;
            try {
                subtitles = podnapisi.search(m.title, m.year, m.release, subtitleLanguage);
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }

            if(isCancelled()) {
                return null;
            }

            if(subtitles == null) {
                if(subtitleLanguage.equals("36") || subtitleLanguage.equals("6") || subtitleLanguage.equals("11")) {
                    try {
                        Titlovi titlovi = new Titlovi();
                        subtitles = titlovi.search(m.title);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                } else {
                    try {
                        subtitles = podnapisi.search(m.title, m.year, m.release, "2");
                    } catch(Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            }

            if(subtitles == null || isCancelled()) {
                return null;
            }

            ArrayList<Subtitle> results = new ArrayList<>();
            for (ArrayList<String> sub : subtitles) {
                String score = String.valueOf(Utils.compareRelease(m.release, sub.get(3)));
                sub.add(score);
                results.add(new Subtitle(sub));
            }

            Collections.sort(results);
            subtitle = results.get(0);
            String zipFile = cacheDir + "/" + subtitle.id + ".zip";
            try {
                Utils.saveURL(subtitle.downloadLink, zipFile);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            String subtitlePath = Utils.unzipSubtitle(zipFile, cacheDir);
            return subtitlePath;
        }

        protected void onPostExecute(String subPath) {
            subtitlePath = subPath;
        }

    }

}
