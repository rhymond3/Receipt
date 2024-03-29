package com.example.receipt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText;
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;
import com.google.firebase.ml.vision.text.RecognizedLanguage;
import com.soundcloud.android.crop.Crop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Context context;
    private String imageFilePath = "";
    private Uri photoURI1;
    private Uri oldPhotoURI;
    private Uri tempUri;
    private Bitmap bitmap;
    private ProgressDialog p;
    private String srcText;

    Task<FirebaseVisionText> result;
    String s ="";
    String results;


    private static final int REQUEST_IMAGE1_CAPTURE = 1;
    int PERMISSION_ALL = 1;
    boolean flagPermissions = false;

    String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET
    };
    private static final String TAG = "MainActivity";

    ImageView imageView;
    TextView textView;

    String textFilePath = "";
    File textFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        context = MainActivity.this;
        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.result);

        if (!flagPermissions) {
            checkPermissions();
        }

        textFile = null;
        try{
            textFile = createTextFile();
        }catch(IOException e){
            e.printStackTrace();
            return;
        }

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCameraIntent();
            }
        });
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

    private void openCameraIntent(){
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(context.getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                return;
            }
            if (photoFile != null) {
                oldPhotoURI = photoURI1;
                photoURI1 = Uri.fromFile(photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI1);
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE1_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_IMAGE1_CAPTURE: {
                if (resultCode == RESULT_OK) {
                    try{
                        Uri source = photoURI1;
                        Uri dest = Uri.fromFile(new File(getCacheDir(), "cropped"));
                        Crop.of(source, dest).start(MainActivity.this);
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                }
                else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "You cancelled the operation", Toast.LENGTH_SHORT).show();
                }
            }
            case Crop.REQUEST_CROP:{
                if(resultCode == RESULT_OK){
                    tempUri = Crop.getOutput(data);
                    Log.i("hello", "Hello");
                    //imageView.setImageURI(tempUri);
                    Log.i("hello", "Hello");

                    AsyncTaskExample asyncTaskExample = new AsyncTaskExample();
                    asyncTaskExample.execute();
                }
            }
        }
    }

    private class AsyncTaskExample extends AsyncTask<String, String, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... strings) {

            try {
                InputStream is = context.getContentResolver().openInputStream(tempUri);
                final BitmapFactory.Options options = new BitmapFactory.Options();
                bitmap = BitmapFactory.decodeStream(is, null, options);

            } catch (Exception ex) {
                Log.i(getClass().getSimpleName(), ex.getMessage());
                Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show();
            }

            runTextRecognition();
            return bitmap;
        }


        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            p = new ProgressDialog(MainActivity.this);
            p.setMessage("Preprocessing");
            p.setIndeterminate(false);
            p.setCancelable(false);
            p.show();
        }

        @Override
        protected void onPostExecute(Bitmap semiResult){
            super.onPostExecute(bitmap);

            if(imageView != null){
                p.hide();
                imageView.setImageBitmap(bitmap);
            }
            else{
                p.show();
            }
        }
    }

    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "IMG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        imageFilePath = image.getAbsolutePath();

        return image;
    }

    void checkPermissions() {
        if (!hasPermissions(context, PERMISSIONS)) {
            requestPermissions(PERMISSIONS,
                    PERMISSION_ALL);
            flagPermissions = false;
        }
        flagPermissions = true;
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    private void runTextRecognition() {
        results = " ";
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionDocumentTextRecognizer textRecognizer = FirebaseVision.getInstance().getCloudDocumentTextRecognizer();
        FirebaseVisionTextRecognizer detector = FirebaseVision.getInstance()
                .getOnDeviceTextRecognizer();
        detector.processImage(image)
                .addOnSuccessListener(
                        new OnSuccessListener<FirebaseVisionText>() {
                            @Override
                            public void onSuccess(FirebaseVisionText texts) {
                                processTextRecognitionResult(texts);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                // Task failed with an exception
                                e.printStackTrace();
                            }
                        });
        /*textRecognizer.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionDocumentText>() {
                    @Override
                    public void onSuccess(FirebaseVisionDocumentText firebaseVisionDocumentText) {
                        Log.i("s ", results);
                        results = processTextRecognitionResult(firebaseVisionDocumentText);

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        e.printStackTrace();
                    }
                });*/
    }

    private void processTextRecognitionResult(FirebaseVisionText texts) {

        String result = "";
        String blockText = "";
        String lineText = "";
        List<RecognizedLanguage> lineLanguages;

        for (FirebaseVisionText.TextBlock block: texts.getTextBlocks()) {
            blockText += block.getText() +"\r";
            Float blockConfidence = block.getConfidence();
            List<RecognizedLanguage> blockLanguages = block.getRecognizedLanguages();
            Point[] blockCornerPoints = block.getCornerPoints();
            Rect blockFrame = block.getBoundingBox();
            for (FirebaseVisionText.Line line: block.getLines()) {
                lineText += line.getText()+ "\n";
                Float lineConfidence = line.getConfidence();
                lineLanguages = line.getRecognizedLanguages();
                Point[] lineCornerPoints = line.getCornerPoints();
                Rect lineFrame = line.getBoundingBox();
                for (FirebaseVisionText.Element element: line.getElements()) {
                    result += element.getText() + "\n";
                    Float elementConfidence = element.getConfidence();
                    List<RecognizedLanguage> elementLanguages = element.getRecognizedLanguages();
                    Point[] elementCornerPoints = element.getCornerPoints();
                    Rect elementFrame = element.getBoundingBox();

                }
            }
        }
        textView.setText(result);


        try{
            FileOutputStream stream = new FileOutputStream(textFilePath);
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(stream);
            outputStreamWriter.append(lineText);
            outputStreamWriter.close();
            stream.close();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    public File createTextFile() throws IOException{
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File text = File.createTempFile("result", ".txt", storageDir);
        textFilePath = text.getAbsolutePath();

        return text;
    }
}