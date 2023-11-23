package com.baidu.paddle.lite.demo.image_classification;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class ImageAdapter extends ArrayAdapter<Bitmap> {
    private Context context;
    private List<Bitmap> images;
    private List<String[]> topResults;
    public ImageAdapter(Context context, List<Bitmap> images, List<String[]> topResults) {
        super(context, 0, images);
        this.context = context;
        this.images = images;
        this.topResults = topResults;
    }

    public void addItems(List<Bitmap> newImages, List<String[]> newResults) {
        images.addAll(newImages);
        topResults.addAll(newResults);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.image_list_item, parent, false);
        }

        Bitmap image = getItem(position);
        ImageView imageView = convertView.findViewById(R.id.image);
        imageView.setImageBitmap(image);

        // 设置每个 TextView 的文本
        TextView topResult1View = convertView.findViewById(R.id.topResult1);
        TextView topResult2View = convertView.findViewById(R.id.topResult2);
        TextView topResult3View = convertView.findViewById(R.id.topResult3);

        String[] results = topResults.get(position);
        topResult1View.setText(results[0]);
        topResult2View.setText(results[1]);
        topResult3View.setText(results[2]);

        return convertView;
    }
}
