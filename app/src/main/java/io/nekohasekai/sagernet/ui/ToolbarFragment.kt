package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R

open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    lateinit var toolbar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        val act = activity as? MainActivity
        if (act != null && act.binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            act.binding.drawerLayout.closeDrawers()
        }

        // 底栏已覆盖主要导航，默认不显示左上角按钮
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
    }

    open fun onKeyDown(ketCode: Int, event: KeyEvent) = false
    open fun onBackPressed(): Boolean = false
}
