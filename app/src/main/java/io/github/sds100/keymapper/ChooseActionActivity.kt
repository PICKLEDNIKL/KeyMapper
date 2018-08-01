package io.github.sds100.keymapper

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import io.github.sds100.keymapper.Action.Companion.TYPE_APP
import io.github.sds100.keymapper.Action.Companion.TYPE_APP_SHORTCUT
import io.github.sds100.keymapper.Action.Companion.TYPE_KEY
import io.github.sds100.keymapper.Action.Companion.TYPE_KEYCODE
import io.github.sds100.keymapper.Action.Companion.TYPE_SYSTEM_ACTION
import io.github.sds100.keymapper.Action.Companion.TYPE_TEXT_BLOCK
import io.github.sds100.keymapper.ActionTypeFragments.*
import io.github.sds100.keymapper.Adapters.ActionTypeSpinnerAdapter
import kotlinx.android.synthetic.main.activity_choose_action.*
import kotlinx.android.synthetic.main.content_choose_action.*


class ChooseActionActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    //The fragments which will each be shown when their corresponding item in the spinner is pressed
    private val mAppActionTypeFragment = AppActionTypeFragment()
    private val mAppShortcutActionTypeFragment = AppShortcutActionTypeFragment()
    private val mKeycodeActionTypeFragment = KeycodeActionTypeFragment()
    private val mKeyActionTypeFragment = KeyActionTypeFragment()
    private val mTextActionTypeFragment = TextActionTypeFragment()
    private val mSystemActionTypeFragment = SystemActionTypeFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_action)
        setSupportActionBar(toolbar)

        //show the back button in the toolbar
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        spinnerActionTypes.adapter = ActionTypeSpinnerAdapter(this)
        spinnerActionTypes.onItemSelectedListener = this
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (mKeyActionTypeFragment.isVisible) {
            mKeyActionTypeFragment.onKeyPress(event!!)
        }

        return super.onKeyUp(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item!!.itemId) {
        /*when the back button in the toolbar is pressed, call onBackPressed so it acts like the
        hardware back button */
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {}

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val actionType = ActionTypeSpinnerAdapter.getActionTypeFromPosition(position)

        when (actionType) {
            TYPE_APP -> showFragmentInFrameLayout(mAppActionTypeFragment)
            TYPE_KEYCODE -> showFragmentInFrameLayout(mKeycodeActionTypeFragment)
            TYPE_APP_SHORTCUT -> showFragmentInFrameLayout(mAppShortcutActionTypeFragment)
            TYPE_KEY -> showFragmentInFrameLayout(mKeyActionTypeFragment)
            TYPE_SYSTEM_ACTION -> showFragmentInFrameLayout(mSystemActionTypeFragment)
            TYPE_TEXT_BLOCK -> showFragmentInFrameLayout(mTextActionTypeFragment)
        }
    }

    private fun showFragmentInFrameLayout(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.frameLayout, fragment).commit()
    }
}
