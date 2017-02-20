package org.howe.cnn;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.renderscript.RenderScript;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import messagepack.ParamUnpacker;
import network.CNNdroid;

import static android.graphics.Color.blue;
import static android.graphics.Color.green;
import static android.graphics.Color.red;

public class MainActivity extends Activity {

    TextView text;
    Button btn;
    RenderScript myRenderScript;
    int imgSize = 0;
    int textSize = 20;
    CNNdroid myConv = null;
    float[][][] mean = null;
    String[] labels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_main);

        text = (TextView) findViewById(R.id.textView);
        text.setText("Press \"Load Model\" to Load Network Parameters...");
        btn = (Button) findViewById(R.id.button);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;

        if (width > 1080)
            imgSize = 1000;
        else if (width > 720) {
            imgSize = 500;
            textSize = 15;
        }
        else {
            imgSize = 300;
            textSize = 10;
        }

        myRenderScript = RenderScript.create(this);

        readLabels();

        new prepareModel().execute(myRenderScript);
    }

    private class prepareModel extends AsyncTask<RenderScript, Void, CNNdroid> {

        ProgressDialog progDailog;
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.layout) ;
        protected void onPreExecute ()
        {
            text.setText("Loading Model Network Parameters...");
            text.setTextSize(textSize);
            btn.setVisibility(View.GONE);
            layout.setClickable(false);
            layout.setFocusable(false);
            layout.setFocusableInTouchMode(false);
            progDailog = new ProgressDialog(MainActivity.this);
            progDailog.setMessage("Please Wait...");
            progDailog.setIndeterminate(false);
            progDailog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progDailog.setCancelable(true);
            progDailog.show();
        }

        @Override
        protected CNNdroid doInBackground(RenderScript... params) {
            try {
                myConv = new CNNdroid(myRenderScript, "/sdcard/Data_Age/age_def.txt");
                ParamUnpacker pu = new ParamUnpacker();
                mean = (float[][][]) pu.unpackerFunction("/sdcard/Data_Age/mean.msg", float[][][].class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return myConv;
        }
        protected void onPostExecute(CNNdroid result) {

            text.setText("\n\n\n\nPress \"Take Photo\" to\nCapture an Object Image...");
            text.setTextSize(textSize + 5);
            text.setTextColor(Color.argb(255,255,222,206));
            btn.setText("Take Photo");
            btn.setVisibility(View.VISIBLE);
            progDailog.dismiss();
            Animation shake = AnimationUtils.loadAnimation(MainActivity.this, R.anim.shake);
            btn.startAnimation(shake);
            layout.setClickable(true);
            layout.setFocusable(true);
            layout.setFocusableInTouchMode(true);
            layout.setOnTouchListener(new RelativeLayout.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Animation shake = AnimationUtils.loadAnimation(MainActivity.this, R.anim.shakemini);
                    btn.startAnimation(shake);
                    return true;
                }
            });
        }
    }


    public void btnClicked(View view)
    {
        takePhoto(view);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private String accuracy(float[] input_matrix, String[] labels, int topk) {

        String result = "";
        int[] max_num = {-1, -1, -1, -1, -1};
        float[] max = new float[topk];
        for (int k = 0; k < topk ; ++k) {
            for (int i = 0; i < 8; ++i) {
                if (input_matrix[i] > max[k]) {
                    boolean newVal = true;
                    for (int j = 0; j < topk; ++j)
                        if (i == max_num[j])
                            newVal = false;
                    if (newVal) {
                        max[k] = input_matrix[i];
                        max_num[k] = i;
                    }
                }
            }
        }

        for (int i = 0 ; i < topk ; i++)
            result += labels[max_num[i]]  + " , P = " + max[i] * 100 + " %\n\n";
        return result;
    }

    public void takePhoto(View view) {
        dispatchTakePictureIntent();
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, 2222);
        }
    }

    private void performCrop(Uri picUri) {
        try {

            Intent cropIntent = new Intent("com.android.camera.action.CROP");
            // indicate image type and Uri
            cropIntent.setDataAndType(picUri, "image/*");
            // set crop properties
            cropIntent.putExtra("crop", "true");
            // indicate aspect of desired crop
            cropIntent.putExtra("aspectX", 1);
            cropIntent.putExtra("aspectY", 1);
            // indicate output X and Y
            cropIntent.putExtra("outputX", 128);
            cropIntent.putExtra("outputY", 128);
            // retrieve data on return
            cropIntent.putExtra("return-data", true);
            // start the activity - we handle returning in onActivityResult
            startActivityForResult(cropIntent, 1);
        }
        // respond to users whose devices do not support the crop action
        catch (ActivityNotFoundException anfe) {
            // display an error message
            String errorMessage = "Whoops - your device doesn't support the crop action!";
            Toast toast = Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // To Handle Camera Result
//        if (data != null && requestCode == 2222) {
//            Uri pic =  data.getData();
//            performCrop(pic);
//        }

        if (data != null) {

            float[][][][] inputBatch = new float[1][3][224][224];
            ImageView img = (ImageView) findViewById(R.id.imageView);
            TextView text = (TextView) findViewById(R.id.textView);


            Bitmap bmp = (Bitmap) data.getExtras().get("data");
            Bitmap bmp1 = Bitmap.createScaledBitmap(bmp, imgSize, imgSize, true);
            Bitmap bmp2 = Bitmap.createScaledBitmap(bmp, 224, 224, false);
            img.setImageBitmap(bmp1);


            for (int j = 0; j < 224; ++j) {
                for (int k = 0; k < 224; ++k) {
                    int color = bmp2.getPixel(j, k);
                    inputBatch[0][0][k][j] = (float) (blue(color)) - mean[0][j][k];
                    inputBatch[0][1][k][j] = (float) (green(color)) - mean[1][j][k];
                    inputBatch[0][2][k][j] = (float) (red(color)) - mean[2][j][k];
                }
            }
            float[][] output = (float[][]) myConv.compute(inputBatch);
            text.setText("\n" + accuracy(output[0], labels, 5));
            text.setTextSize(textSize);
            Animation alpha = AnimationUtils.loadAnimation(this, R.anim.alpha);
            text.startAnimation(alpha);

            Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
            btn.startAnimation(shake);
        }
    }

    private void readLabels() {
        labels = new String[1000];
        File f = new File("/sdcard/Data_Age/labels.txt");
        Scanner s = null;
        int iter = 0;

        try {
            s = new Scanner(f);
            while (s.hasNextLine()) {
                String str = s.nextLine();
                labels[iter++] = str;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
