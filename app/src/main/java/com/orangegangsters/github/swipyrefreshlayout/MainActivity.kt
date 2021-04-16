package com.orangegangsters.github.swipyrefreshlayout

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.orangegangsters.github.swiperefreshlayout.R
import com.orangegangsters.github.swiperefreshlayout.databinding.ActivityMainBinding
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout.OnRefreshListener
import com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayoutDirection

class MainActivity : AppCompatActivity(), OnRefreshListener, View.OnClickListener {
    /**
     * This view binding
     */
    private var mBinding: ActivityMainBinding? = null
    private val binding
        get() = mBinding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initLayout()
    }

    private fun initLayout() {
        binding.listview.adapter = DummyListViewAdapter(this)
        binding.swipyrefreshlayout.setOnRefreshListener(this)
        binding.buttonTop.setOnClickListener(this)
        binding.buttonBottom.setOnClickListener(this)
        binding.buttonBoth.setOnClickListener(this)
        binding.buttonRefresh.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.button_top -> binding.swipyrefreshlayout.setDirection(SwipyRefreshLayoutDirection.TOP)
            R.id.button_bottom -> binding.swipyrefreshlayout.setDirection(SwipyRefreshLayoutDirection.BOTTOM)
            R.id.button_both -> binding.swipyrefreshlayout.setDirection(SwipyRefreshLayoutDirection.BOTH)
            R.id.button_refresh -> {
                binding.swipyrefreshlayout.isRefreshing = true
                Handler(Looper.getMainLooper()).postDelayed({ //Hide the refresh after 2sec
                    runOnUiThread { mBinding?.swipyrefreshlayout?.isRefreshing = false }
                }, 2000)
            }
        }
    }

    /**
     * Called when the [com.orangegangsters.github.swipyrefreshlayout.library.SwipyRefreshLayout]
     * is in refresh mode. Just for example purpose.
     */
    override fun onRefresh(direction: SwipyRefreshLayoutDirection?) {
        Log.d("MainActivity", "Refresh triggered at "
                + if (direction === SwipyRefreshLayoutDirection.TOP) "top" else "bottom")
        Handler(Looper.getMainLooper()).postDelayed({ //Hide the refresh after 2sec
            runOnUiThread { mBinding?.swipyrefreshlayout?.isRefreshing = false }
        }, DISMISS_TIMEOUT)
    }

    companion object {
        /**
         * The dismiss time for [SwipyRefreshLayout]
         */
        const val DISMISS_TIMEOUT = 2000L
    }
}