package com.orangegangsters.github.swipyrefreshlayout

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.orangegangsters.github.swiperefreshlayout.R

/**
 * Created by oliviergoutay on 1/23/15.
 */
class DummyListViewAdapter(private val mContext: Context) : BaseAdapter() {
    private val dummyStrings: List<String> = listOf(
            "You want",
            "to test",
            "this library",
            "from both",
            "direction.",
            "You may",
            "be amazed",
            "when done",
            "so!",
            "I am",
            "going to",
            "add a little",
            "more lines",
            "for big",
            "smartphones."
    )

    override fun getCount(): Int {
        return dummyStrings.size
    }

    override fun getItem(position: Int): Any {
        return dummyStrings[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val cv = convertView ?: run {
            val inflater = (mContext as Activity).layoutInflater
            val convertViewNew = inflater.inflate(R.layout.listview_cell, parent, false)
            val viewHolder = ViewHolder().apply {
                mCellNumber = convertViewNew.findViewById(R.id.cell_number)
                mCellText = convertViewNew.findViewById(R.id.cell_text)
            }
            convertViewNew.tag = viewHolder
            convertViewNew
        }
        (cv.tag as ViewHolder).apply {
            mCellNumber?.text = position.toString()
            mCellText?.text = dummyStrings[position]
        }
        return cv
    }

    internal class ViewHolder {
        var mCellNumber: TextView? = null
        var mCellText: TextView? = null
    }
}