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

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HSLTransitData extends TransitData
{

    private String        mSerialNumber;
    private double     mBalance;
    private HSLTrip[] mTrips;
    private boolean mHasKausi;
	private long mKausiStart;
	private long mKausiEnd;
	private long mKausiPrevStart;
	private long mKausiPrevEnd;
	private long mKausiPurchasePrice;
	private long mKausiLastUse;
	private long mKausiPurchase;
	private long mLastRefillTime;
	private HSLRefill mLastRefill;
	
	private boolean mKausiNoData;
	private long mLastRefillAmount;

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
            mLastRefill = new HSLRefill(data);
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
            if(bitsToLong(19,14,data)==0)
            	mKausiNoData = true;
            mKausiStart = CardDateToTimestamp(bitsToLong(19,14,data),0);
            mKausiEnd = CardDateToTimestamp(bitsToLong(33,14,data),0);
            mKausiPrevStart = CardDateToTimestamp(bitsToLong(67,14,data),0);
            mKausiPrevEnd = CardDateToTimestamp(bitsToLong(81,14,data),0);
            mKausiPurchase = CardDateToTimestamp(bitsToLong(110,14,data),bitsToLong(124,11,data));
            mKausiPurchasePrice = bitsToLong(149,15,data);
            mKausiLastUse = CardDateToTimestamp(bitsToLong(192,14,data),bitsToLong(206,11,data));
        } catch (Exception ex) {
            throw new RuntimeException("Error parsing HSL kausi data", ex);
        }
    }

    public static long CardDateToTimestamp(long day, long minute) {
    	return (EPOCH) + day * (60*60*24) + minute * 60;
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
    public String getCustomString () {
    	StringBuilder ret = new StringBuilder();
    	if(!mKausiNoData){
    		ret.append("Current Pass starts: ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiStart*1000.0));
	    	ret.append("\nCurrent Pass ends: ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiEnd*1000.0)); 
	    	ret.append("\n\nPass bought on ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(mKausiPurchase*1000.0));
	    	ret.append(" for ").append(NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mKausiPurchasePrice / 100.0));
	    	ret.append("\nYou last used this pass on ").append(SimpleDateFormat.getDateTimeInstance(DateFormat.LONG,DateFormat.SHORT).format(mKausiLastUse*1000.0));
	    	ret.append("\n\nPrevious kausi was: ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiPrevStart*1000.0));
	    	ret.append(" - ").append(SimpleDateFormat.getDateInstance(DateFormat.SHORT).format(mKausiPrevEnd*1000.0));
    	}else
    		return null;
    	return ret.toString();
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
    	Refill[] ret ={mLastRefill};
        return ret;
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
    public static class HSLRefill extends Refill {
    	private final long mRefillTime;
    	private final long mRefillAmount;
    	public HSLRefill(byte[] data) {
            mRefillTime = CardDateToTimestamp(bitsToLong(20,14,data),bitsToLong(34,11,data));
            mRefillAmount = bitsToLong(45,20,data);
    	}
        public HSLRefill (Parcel parcel) {
            mRefillTime = parcel.readLong();
            mRefillAmount = parcel.readLong();
        }
    	
		public void writeToParcel(Parcel dest, int flags) {
	        dest.writeLong(mRefillTime);
	        dest.writeLong(mRefillAmount);
		}

		@Override
		public long getTimestamp() {
			return mRefillTime;
		}

		@Override
		public String getAgencyName() {
			return "Arvon lataus";
		}

		@Override
		public String getShortAgencyName() {
			return "Arvon lataus";
		}

		@Override
		public long getAmount() {
			return mRefillAmount;
		}

		@Override
		public String getAmountString() {
			return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(mRefillAmount / 100.0);
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
            
            mTimestamp = CardDateToTimestamp(day,minutes);
    
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
            if(mAgency==1)
                return "Seutulippu";
            return "Sisäinen lippu";
        }

        @Override
        public String getShortAgencyName () {
           if(mAgency==1)
                    return "Seutulippu";
            return "Sisäinen lippu";
        }

        @Override
        public String getRouteName () {
            if(mArvo==1)
            	return "Arvolla";
            else
           		return "Kaudella";
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
            return false;
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
