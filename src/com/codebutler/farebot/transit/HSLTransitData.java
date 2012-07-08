/*
 * OrcaTransitData.java
 *
 * Copyright (C) 2011 Eric Butler
 *
 * Authors:
 * Eric Butler <eric@codebutler.com>
 *
 * Thanks to:
 * Karl Koscher <supersat@cs.washington.edu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.codebutler.farebot.transit;

import android.os.Parcel;
import com.codebutler.farebot.Utils;
import com.codebutler.farebot.mifare.Card;
import com.codebutler.farebot.mifare.DesfireCard;
import com.codebutler.farebot.mifare.DesfireFile;
import com.codebutler.farebot.mifare.DesfireFile.RecordDesfireFile;
import com.codebutler.farebot.mifare.DesfireRecord;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HSLTransitData extends TransitData
{
    private static final int AGENCY_KCM = 0x04;
    private static final int AGENCY_PT  = 0x06;
    private static final int AGENCY_ST  = 0x07;
    private static final int AGENCY_CT  = 0x02;

    // For future use.
    private static final int TRANS_TYPE_PURSE_USE   = 0x0c;
    private static final int TRANS_TYPE_CANCEL_TRIP = 0x01;
    private static final int TRANS_TYPE_TAP_IN      = 0x03;
    private static final int TRANS_TYPE_TAP_OUT     = 0x07;
    private static final int TRANS_TYPE_PASS_USE    = 0x60;

    private String        mSerialNumber;
    private double     mBalance;
    private HSLTrip[] mTrips;

    public static boolean check (Card card)
    {
        return (card instanceof DesfireCard) && (((DesfireCard) card).getApplication(0x1120ef) != null);
    }

    public static TransitIdentity parseTransitIdentity(Card card)
    {
        try {
            byte[] data = ((DesfireCard) card).getApplication(0x1120ef).getFile(0x08).getData();
            return new TransitIdentity("HSL", Utils.getHexString(data).substring(2, 20));
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL serial", ex);
        }
    }

    public HSLTransitData (Parcel parcel) {
        mSerialNumber = parcel.readString();
        mBalance      = parcel.readDouble();
        
        mTrips = new HSLTrip[parcel.readInt()];
        parcel.readTypedArray(mTrips, HSLTrip.CREATOR);
    }
    
    public HSLTransitData (Card card)
    {
        DesfireCard desfireCard = (DesfireCard) card;

        byte[] data;

        try {
            data = desfireCard.getApplication(0x1120ef).getFile(0x08).getData();
            mSerialNumber = Utils.getHexString(data).substring(2, 20);  //Utils.byteArrayToInt(data, 1, 9);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL serial", ex);
        }

        try {
            data = desfireCard.getApplication(0x1120ef).getFile(0x02).getData();
            mBalance = Utils.byteArrayToInt(data, 0, 3) >> 4;
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL balance", ex);
        }

        try {
            mTrips = parseTrips(desfireCard);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL trips", ex);
        }
    }

    @Override
    public String getCardName () {
        return "HSL";
    }

    @Override
    public String getBalanceString () {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mBalance / 100);
    }

    @Override
    public String getSerialNumber () {
        return mSerialNumber;
    }

    @Override
    public Trip[] getTrips () {
        return mTrips;
    }

    @Override
    public Refill[] getRefills () {
        return null;
    }

    private HSLTrip[] parseTrips (DesfireCard card)
    {
        DesfireFile file = card.getApplication(0x1120ef).getFile(0x04);

        if (file instanceof RecordDesfireFile) {
            RecordDesfireFile recordFile = (RecordDesfireFile) card.getApplication(0x1120ef).getFile(0x04);

            List<Trip> result = new ArrayList<Trip>();

            HSLTrip[] useLog = new HSLTrip[recordFile.getRecords().length];
            for (int i = 0; i < useLog.length; i++) {
                useLog[i] = new HSLTrip(recordFile.getRecords()[i]);
            }

            Arrays.sort(useLog, new Trip.Comparator());

            return useLog;
        }
        return new HSLTrip[0];
    }

    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mSerialNumber);
        parcel.writeDouble(mBalance);

        if (mTrips != null) {
            parcel.writeInt(mTrips.length);
            parcel.writeTypedArray(mTrips, flags);
        } else {
            parcel.writeInt(0);
        }
    }

    public static class HSLTrip extends Trip
    {
        private final long mTimestamp;
        private final long mCoachNum;
        private final long mFare;
        private final long mNewBalance;
        private final long mAgency;
        private final long mTransType;

        private static Station[] sLinkStations = new Station[] {
            new Station("Westlake Station",                   "Westlake",      "47.6113968", "-122.337502"),
            new Station("University Station",                 "University",    "47.6072502", "-122.335754"),
            new Station("Pioneer Square Station",             "Pioneer Sq",    "47.6021461", "-122.33107"),
            new Station("International District Station",     "ID",            "47.5976601", "-122.328217"),
            new Station("Stadium Station",                    "Stadium",       "47.5918121", "-122.327354"),
            new Station("SODO Station",                       "SODO",          "47.5799484", "-122.327515"),
            new Station("Beacon Hill Station",                "Beacon Hill",   "47.5791245", "-122.311287"),
            new Station("Mount Baker Station",                "Mount Baker",   "47.5764389", "-122.297737"),
            new Station("Columbia City Station",              "Columbia City", "47.5589523", "-122.292343"),
            new Station("Othello Station",                    "Othello",       "47.5375366", "-122.281471"),
            new Station("Rainier Beach Station",              "Rainier Beach", "47.5222626", "-122.279579"),
            new Station("Tukwila International Blvd Station", "Tukwila",       "47.4642754", "-122.288391"),
            new Station("Seatac Airport Station",             "Sea-Tac",       "47.4445305", "-122.297012")
        };
        
        public long bitsToLong(int start, long[] data){
        	long ret=0;
        	for(int i=start;i<start+32;++i){
        		long bit=((data[i/8] >> (7 - i%8)) & 1);
        		ret = ret | (bit << ((start+32)-i));
        	}
        	return ret;
        }
        
        public HSLTrip (DesfireRecord record)
        {
            byte[] useData = record.getData();
            long[] usefulData = new long[useData.length];
    
            for (int i = 0; i < useData.length; i++) {
                usefulData[i] = ((long)useData[i]) & 0xFF;
            }
    
         /*   mTimestamp =
                ((0x0F & usefulData[0]) << 28) |
                (usefulData[1] << 20) |
                (usefulData[2] << 12) |
                (usefulData[3] << 4)  |
                (usefulData[4] >> 4); */
            
            long day =((usefulData[3] & 0x0F) << 8) |
            		  (usefulData[4]);
            
         //   long secs = 
            
            mTimestamp = (1205989200) + day * (60*60*24);
            
            /*mTimestamp = //bitsToLong(0,usefulData) / 4;
                   // (((usefulData[0]) << 24) |
                    (usefulData[1] << 16) |
                    (usefulData[2] << 8) |
                    (usefulData[3] << 0); //|
                    //(usefulData[4] );
            */
    
            mCoachNum = ((usefulData[9] & 0xf) << 12) | (usefulData[10] << 4) | ((usefulData[11] & 0xf0) >> 4);

            /*if (usefulData[15] == 0x00 || usefulData[15] == 0xFF) {
                // FIXME: This appears to be some sort of special case for transfers and passes.
                mFare = 0;
            } else {
                mFare = (usefulData[15] << 7) | (usefulData[16] >> 1);
            }
			
            mNewBalance = (usefulData[34] << 8) | usefulData[35];*/
            mFare=mNewBalance=mTransType=0;
            mAgency     = usefulData[3] >> 4;
            //mTransType  = (usefulData[17]);
        }
        
        public static Creator<HSLTrip> CREATOR = new Creator<HSLTrip>() {
            public HSLTrip createFromParcel(Parcel parcel) {
                return new HSLTrip(parcel);
            }

            public HSLTrip[] newArray(int size) {
                return new HSLTrip[size];
            }
        };

        private HSLTrip (Parcel parcel)
        {
            mTimestamp  = parcel.readLong();
            mCoachNum   = parcel.readLong();
            mFare       = parcel.readLong();
            mNewBalance = parcel.readLong();
            mAgency     = parcel.readLong();
            mTransType  = parcel.readLong();
        }

        @Override
        public long getTimestamp() {
            return mTimestamp;
        }

        @Override
        public String getAgencyName () {
            switch ((int) mAgency) {
                case AGENCY_CT:
                    return "Community Transit";
                case AGENCY_KCM:
                    return "King County Metro Transit";
                case AGENCY_PT:
                    return "Pierce Transit";
                case AGENCY_ST:
                    return "Sound Transit";
            }
            return "Unknown Agency";
        }

        @Override
        public String getShortAgencyName () {
            switch ((int) mAgency) {
                case AGENCY_CT:
                    return "CT";
                case AGENCY_KCM:
                    return "KCM";
                case AGENCY_PT:
                    return "PT";
                case AGENCY_ST:
                    return "ST";
            }
            return "Unknown";
        }

        @Override
        public String getRouteName () {
            if (isLink()) {
                return "Link Light Rail";
            } else {
                // FIXME: Need to find bus route #s
                if (mAgency == AGENCY_ST) {
                    return "Express Bus";
                } else if(mAgency == AGENCY_KCM) {
                    return "Bus";
                }
                return null;
            }
        }

        @Override
        public String getFareString () {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mFare / 100.0);
        }

        @Override
        public double getFare () {
            return mFare;
        }

        @Override
        public String getBalanceString () {
            return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mNewBalance / 100);
        }

        @Override
        public Station getStartStation() {
            if (isLink()) {
                return sLinkStations[(((int) mCoachNum) % 1000) - 193];
            }
            return null;
        }

        @Override
        public String getStartStationName () {
            if (isLink()) {
                return sLinkStations[(((int) mCoachNum) % 1000) - 193].getStationName();
            } else {
                return "Coach #" + String.valueOf(mCoachNum);
            }
        }

        @Override
        public String getEndStationName () {
            // ORCA tracks destination in a separate record
            return null;
        }

        @Override
        public Station getEndStation () {
            // ORCA tracks destination in a separate record
            return null;
        }

        @Override
        public Mode getMode() {
            return (isLink()) ? Mode.METRO : Mode.BUS;
        }

        public long getCoachNumber() {
            return mCoachNum;
        }

        public void writeToParcel(Parcel parcel, int flags)
        {
            parcel.writeLong(mTimestamp);
            parcel.writeLong(mCoachNum);
            parcel.writeLong(mFare);
            parcel.writeLong(mNewBalance);
            parcel.writeLong(mAgency);
            parcel.writeLong(mTransType);
        }

        public int describeContents() {
            return 0;
        }

        private boolean isLink () {
            return (mAgency == HSLTransitData.AGENCY_ST && mCoachNum > 10000);
        }
    }
}
