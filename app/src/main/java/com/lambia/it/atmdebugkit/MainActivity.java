package com.lambia.it.atmdebugkit;

import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private NfcAdapter nfcAdapter;
    TextView textViewInfo, textViewTagInfo, textViewBlock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textViewInfo = (TextView)findViewById(R.id.info);
        textViewTagInfo = (TextView)findViewById(R.id.taginfo);
        textViewBlock = (TextView)findViewById(R.id.block);

        textViewInfo.setMovementMethod(new ScrollingMovementMethod());
        textViewTagInfo.setMovementMethod(new ScrollingMovementMethod());
        textViewBlock.setMovementMethod(new ScrollingMovementMethod());

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if(nfcAdapter == null){
            Toast.makeText(this,
                    "NFC NOT supported on this devices!",
                    Toast.LENGTH_LONG).show();
            finish();
        }else if(!nfcAdapter.isEnabled()){
            //ToDo: send user to settings
            Toast.makeText(this,
                    "NFC NOT Enabled!",
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        // ToDo: make this work, could be achieved using an IntentFilter in the code instead of manifest
        super.onResume();

        Intent intent = getIntent();
        String action = intent.getAction();

        if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
            Toast.makeText(this,
                    "onResume() - ACTION_TECH_DISCOVERED",
                    Toast.LENGTH_SHORT).show();

            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            if(tag == null){
                textViewInfo.setText("tag == null");
            }else{
                String tagInfo = tag.toString() + "\n";

                tagInfo += "\nTag Id: \n";
                byte[] tagId = tag.getId();
                tagInfo += "length = " + tagId.length +"\n";
                for(int i=0; i<tagId.length; i++){
                    tagInfo += String.format("%02X", tagId[i] & 0xff) + " ";
                }
                tagInfo += "\n";

                String[] techList = tag.getTechList();
                tagInfo += "\nTech List\n";
                tagInfo += "length = " + techList.length +"\n";
                for(int i=0; i<techList.length; i++){
                    tagInfo += techList[i] + "\n ";
                }

                textViewInfo.setText(tagInfo);

                //Only android.nfc.tech.MifareClassic specified in nfc_tech_filter.xml,
                //so must be MifareClassic
                readMifareClassic(tag);
            }
        }else{
            Toast.makeText(this,
                    "onResume() : " + action,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void readMifareClassic(Tag tag){
        MifareClassic mifareClassicTag = MifareClassic.get(tag);

        String typeInfoString = "--- MifareClassic tag ---\n";
        int type = mifareClassicTag.getType();
        switch(type){
            case MifareClassic.TYPE_PLUS:
                typeInfoString += "MifareClassic.TYPE_PLUS\n";
                break;
            case MifareClassic.TYPE_PRO:
                typeInfoString += "MifareClassic.TYPE_PRO\n";
                break;
            case MifareClassic.TYPE_CLASSIC:
                typeInfoString += "MifareClassic.TYPE_CLASSIC\n";
                break;
            case MifareClassic.TYPE_UNKNOWN:
                typeInfoString += "MifareClassic.TYPE_UNKNOWN\n";
                break;
            default:
                typeInfoString += "unknown...!\n";
        }

        int size = mifareClassicTag.getSize();
        switch(size){
            case MifareClassic.SIZE_1K:
                typeInfoString += "MifareClassic.SIZE_1K\n";
                break;
            case MifareClassic.SIZE_2K:
                typeInfoString += "MifareClassic.SIZE_2K\n";
                break;
            case MifareClassic.SIZE_4K:
                typeInfoString += "MifareClassic.SIZE_4K\n";
                break;
            case MifareClassic.SIZE_MINI:
                typeInfoString += "MifareClassic.SIZE_MINI\n";
                break;
            default:
                typeInfoString += "unknown size...!\n";
        }

        int blockCount = mifareClassicTag.getBlockCount();
        typeInfoString += "BlockCount \t= " + blockCount + "\n";
        int sectorCount = mifareClassicTag.getSectorCount();
        typeInfoString += "SectorCount \t= " + sectorCount + "\n";

        textViewTagInfo.setText(typeInfoString);

        new ReadMifareClassicTask(mifareClassicTag).execute();

    }

    private class ReadMifareClassicTask extends AsyncTask<Void, Void, Void> {

        /*
        MIFARE Classic tags are divided into sectors, and each sector is sub-divided into blocks.
        Block size is always 16 bytes (BLOCK_SIZE). Sector size varies.
        MIFARE Classic 1k are 1024 bytes (SIZE_1K), with 16 sectors each of 4 blocks.
        */

        MifareClassic taskTag;
        int numOfBlock;
        final int FIX_SECTOR_COUNT = 16;
        boolean success;
        final int numOfSector = 16;
        final int numOfBlockInSector = 4;
        byte[][][] buffer = new byte[numOfSector][numOfBlockInSector][MifareClassic.BLOCK_SIZE];

        ReadMifareClassicTask(MifareClassic tag){
            taskTag = tag;
            success = false;
        }

        @Override
        protected void onPreExecute() {
            textViewBlock.setText("Reading Tag, don't remove it!");
        }

        @Override
        protected Void doInBackground(Void... params) {

            try {
                taskTag.connect();

                /* ToDo: insert keys here */
                for(int s=0; s<numOfSector; s++){
                    if(taskTag.authenticateSectorWithKeyA(s, MifareClassic.KEY_DEFAULT)) {
                        for(int b=0; b<numOfBlockInSector; b++){
                            int blockIndex = (s * numOfBlockInSector) + b;
                            buffer[s][b] = taskTag.readBlock(blockIndex);
                        }
                    }
                }

                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            } finally{
                if(taskTag!=null){
                    try {
                        taskTag.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            //display block
            if(success){
                String stringBlock = "";
                for(int i=0; i<numOfSector; i++){
                    stringBlock += i + " :\n";
                    for(int j=0; j<numOfBlockInSector; j++){
                        for(int k=0; k<MifareClassic.BLOCK_SIZE; k++){
                            stringBlock += String.format("%02X", buffer[i][j][k] & 0xff) + " ";
                        }
                        stringBlock += "\n";
                    }
                    stringBlock += "\n";
                }
                textViewBlock.setText(stringBlock);
            }else{
                textViewBlock.setText("Fail to read Blocks!!!");
            }
        }
    }
}
