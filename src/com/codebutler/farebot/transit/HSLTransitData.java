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
    private boolean mHasKausi;

    private static final long EPOCH = 0x32C97ED0;
    
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
    public static long bitsToLong(int start, int len, byte[] data){
    	long ret=0;
    	for(int i=start;i<start+len;++i){
    		long bit=((data[i/8] >> (7 - i%8)) & 1);
    		ret = ret | (bit << ((start+len-1)-i));
    	}
    	return ret;
    }
    public static long bitsToLong(int start, int len, long[] data){
    	long ret=0;
    	for(int i=start;i<start+len;++i){
    		long bit=((data[i/8] >> (7 - i%8)) & 1);
    		ret = ret | (bit << ((start+len-1)-i));
    	}
    	return ret;
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
            mBalance = bitsToLong(0,20,data);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL balance", ex);
        }

        try {
            mTrips = parseTrips(desfireCard);
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL trips", ex);
        }
        try {
        	data = desfireCard.getApplication(0x1120ef).getFile(0x01).getData();
            mHasKausi = bitsToLong(33,14,data) > ((System.currentTimeMillis()/1000 - EPOCH) / (60*60*24));
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
    	String ret =  NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mBalance / 100);
    	if(mHasKausi)
    		ret +="\nkautta 31.8.2012 asti";
        return ret;
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
        private final long mArvo;

        

        
        public HSLTrip (DesfireRecord record)
        {
            byte[] useData = record.getData();
            long[] usefulData = new long[useData.length];
    
            for (int i = 0; i < useData.length; i++) {
                usefulData[i] = ((long)useData[i]) & 0xFF;
            }
    
            
            long day = bitsToLong(1, 14, usefulData);
            
            long minutes = bitsToLong(15,11,usefulData); 
            
            mTimestamp = (EPOCH) + day * (60*60*24) + minutes * 60;
    
            mCoachNum = bitsToLong(79,10,usefulData);

            mFare= bitsToLong(51,14,usefulData); 
            		
            mNewBalance=mTransType=0;
            mAgency     = bitsToLong(51,7,usefulData);
            
            mArvo = bitsToLong(0,1,usefulData);
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
            mArvo  		= parcel.readLong();
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
                    return "Espoosta ostettu";
                case AGENCY_KCM:
                    return "KCM";
                case AGENCY_PT:
                    return "PT";
                case AGENCY_ST:
                    return "ST";
            }
            return "Helsingistä ostettu";
        }

        @Override
        public String getRouteName () {
            if(mArvo==1)
            	return "Arvolippu";
            else
           		return "Kausilippu";
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
            parcel.writeLong(mArvo);
        }

        public int describeContents() {
            return 0;
        }

        private boolean isLink () {
            return (mAgency == HSLTransitData.AGENCY_ST && mCoachNum > 10000);
        }

		@Override
		public String getStartStationName() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Station getStartStation() {
			// TODO Auto-generated method stub
			return null;
		}
    }
}
