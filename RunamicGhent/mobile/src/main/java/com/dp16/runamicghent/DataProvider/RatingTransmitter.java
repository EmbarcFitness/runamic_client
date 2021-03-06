/*
 * Copyright (c) 2017 Hendrik Depauw
 * Copyright (c) 2017 Lorenz van Herwaarden
 * Copyright (c) 2017 Nick Aelterman
 * Copyright (c) 2017 Olivier Cammaert
 * Copyright (c) 2017 Maxim Deweirdt
 * Copyright (c) 2017 Gerwin Dox
 * Copyright (c) 2017 Simon Neuville
 * Copyright (c) 2017 Stiaan Uyttersprot
 *
 * This software may be modified and distributed under the terms of the MIT license.  See the LICENSE file for details.
 */

package com.dp16.runamicghent.DataProvider;

import android.util.Log;

import com.dp16.runamicghent.Constants;
import com.dp16.runamicghent.RunData.RunRating;
import com.dp16.eventbroker.EventBroker;
import com.dp16.eventbroker.EventListener;
import com.dp16.eventbroker.EventPublisher;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This DataProvider will send ratings of runs to the server. Not really a DataProvider.
 *
 * <p>
 *     <b>Messages Produced: </b> {@link com.dp16.runamicghent.Constants.EventTypes#STATUS_CODE}
 * </p>
 * <p>
 *     <b>Messages Consumed: </b> {@link com.dp16.runamicghent.Constants.EventTypes#RATING}
 * </p>
 * Created by lorenzvanherwaarden on 29/04/2017.
 */

public class RatingTransmitter implements EventPublisher, EventListener, DataProvider {

    private ExecutorService worker;

    private Boolean statusReponse;

    public RatingTransmitter(Boolean statusResponse) {
        this.statusReponse = statusResponse;
        worker = Executors.newSingleThreadExecutor();
        this.start();
    }

    /**
     * Start listening to Rating Events.
     */
    @Override
    public void start() {
        EventBroker.getInstance().addEventListener(Constants.EventTypes.RATING, this);
    }

    @Override
    public void stop() {
        EventBroker.getInstance().removeEventListener(Constants.EventTypes.RATING, this);
    }

    @Override
    public void resume() {
        start();
    }

    @Override
    public void pause() {
        stop();
    }

    /**
     * Handles the reception of RATING events.
     *
     * @param eventType: should be of type Constants.EventTypes.RATING.
     * @param message:   RunRating class with tag and rating of run
     */
    @Override
    public void handleEvent(String eventType, Object message) {
        Runnable task = new RatingTransmitter.Worker(message);
        worker.submit(task);
    }

    /**
     * Asynchronous providing of rating for run to the server.
     */
    private class Worker implements Runnable {
        private RunRating runRating;
        private RatingTransmitter ratingTransmitter;

        Worker(Object message) {
            this.runRating = (RunRating) message;
        }

        /*
          Open connection and fetch the response from the server for adding a rating.
         */
        @Override
        public void run() {
            // Construct the URL.
            URL url = constructURL();

            boolean goodRequest = false;
            int amountOfTries = 3;
            int status = 0;
            while (amountOfTries > 0 && !goodRequest) {
                if (url != null) {
                    try {
                        //open connection w/ URL
                        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                        status = httpURLConnection.getResponseCode();

                        if (status == 200) {
                            goodRequest = true;
                            Log.d("tag", runRating.getTag());
                        }
                    } catch (Exception e) {
                        Log.e("Sending Rating", e.getLocalizedMessage(), e);
                        amountOfTries--;
                    }
                }
            }
            // If rating was not able to be sent in 3 requests, too bad. This is not such a big problem.
            Log.d("Rating sent", Boolean.toString(goodRequest));

            // If statusReponse is set to true, an event will be published with the status code.
            if (statusReponse){
                EventBroker.getInstance().addEvent(Constants.EventTypes.STATUS_CODE, status, this.ratingTransmitter);
            }
        }

        /**
         * Construct the URL that will be used to publish rating on server
         *
         * @return {@link String} URL
         */
        private URL constructURL() {
            String urlString;

            if (Constants.DEVELOP) {
                urlString = "https://groep16.cammaert.me/develop/route/rate?"; //Develop server
            } else {
                urlString = "https://groep16.cammaert.me/app/route/rate?"; //Master server
            }

            // Add Tag and Rating to url
            urlString = urlString.concat("tag=" + runRating.getTag());
            urlString = urlString.concat("&rating=" + runRating.getStringRating());

            URL url = null;
            try {
                url = new URL(urlString);
            } catch (MalformedURLException e) {
                Log.e("constructURL", e.getMessage(), e);
            }

            return url;
        }
    }

}
