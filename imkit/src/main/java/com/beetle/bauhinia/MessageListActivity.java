package com.beetle.bauhinia;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.support.v7.widget.Toolbar;

import com.beetle.bauhinia.db.Conversation;
import com.beetle.bauhinia.db.ConversationIterator;
import com.beetle.bauhinia.db.GroupMessageDB;
import com.beetle.bauhinia.db.IMessage;
import com.beetle.bauhinia.db.IMessage.GroupNotification;
import com.beetle.bauhinia.db.PeerMessageDB;
import com.beetle.im.IMMessage;
import com.beetle.im.IMService;
import com.beetle.im.IMServiceObserver;
import com.beetle.im.LoginPoint;
import com.beetle.bauhinia.activity.BaseActivity;
import com.beetle.bauhinia.tools.*;
import com.beetle.bauhinia.tools.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import com.beetle.imkit.R;

public class MessageListActivity extends BaseActivity implements IMServiceObserver, AdapterView.OnItemClickListener,
         NotificationCenter.NotificationCenterObserver {
    private static final String TAG = "beetle";

    private List<Conversation> conversations;
    private ListView lv;
    protected long currentUID = 0;

    private BaseAdapter adapter;
    class ConversationAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return conversations.size();
        }
        @Override
        public Object getItem(int position) {
            return conversations.get(position);
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ConversationView view = null;
            if (convertView == null) {
                view = new ConversationView(MessageListActivity.this);
            } else {
                view = (ConversationView)convertView;
            }
            Conversation c = conversations.get(position);
            view.setConversation(c);;
            return view;
        }
    }

    // 初始化组件
    private void initWidget() {
        Toolbar toolbar = (Toolbar)findViewById(R.id.support_toolbar);
        setSupportActionBar(toolbar);

        lv = (ListView) findViewById(R.id.list);
        adapter = new ConversationAdapter();
        lv.setAdapter(adapter);
        lv.setOnItemClickListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "main activity create...");

        setContentView(R.layout.activity_conversation);

        Intent intent = getIntent();

        currentUID = intent.getLongExtra("current_uid", 0);
        if (currentUID == 0) {
            Log.e(TAG, "current uid is 0");
            return;
        }

        IMService im =  IMService.getInstance();
        im.addObserver(this);

        loadConversations();
        initWidget();

        NotificationCenter nc = NotificationCenter.defaultCenter();
        nc.addObserver(this, PeerMessageActivity.SEND_MESSAGE_NAME);
        nc.addObserver(this, PeerMessageActivity.CLEAR_MESSAGES);
        nc.addObserver(this, GroupMessageActivity.SEND_MESSAGE_NAME);
        nc.addObserver(this, GroupMessageActivity.CLEAR_MESSAGES);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IMService im =  IMService.getInstance();
        im.removeObserver(this);
        NotificationCenter nc = NotificationCenter.defaultCenter();
        nc.removeObserver(this);
        Log.i(TAG, "message list activity destroyed");
    }


    public  String messageContentToString(IMessage.MessageContent content) {
        if (content instanceof IMessage.Text) {
            return ((IMessage.Text) content).text;
        } else if (content instanceof IMessage.Image) {
            return "一张图片";
        } else if (content instanceof IMessage.Audio) {
            return "一段语音";
        } else if (content instanceof IMessage.GroupNotification) {
            return ((GroupNotification) content).description;
        } else if (content instanceof IMessage.Location) {
            return "一个地理位置";
        } else {
            return content.getRaw();
        }
    }

    void updateConversationDetail(Conversation conv) {
        String detail = messageContentToString(conv.message.content);
        conv.setDetail(detail);
    }

    void updatePeerConversationName(Conversation conv) {
        User u = getUser(conv.cid);
        if (TextUtils.isEmpty(u.name)) {
            conv.setName(u.identifier);
            final Conversation fconv = conv;
            asyncGetUser(conv.cid, new GetUserCallback() {
                @Override
                public void onUser(User u) {
                    fconv.setName(u.name);
                    fconv.setAvatar(u.avatarURL);
                }
            });
        } else {
            conv.setName(u.name);
        }
        conv.setAvatar(u.avatarURL);
    }

    void updateGroupConversationName(Conversation conv) {
        Group g = getGroup(conv.cid);
        if (TextUtils.isEmpty(g.name)) {
            conv.setName(g.identifier);
            final Conversation fconv = conv;
            asyncGetGroup(conv.cid, new GetGroupCallback() {
                @Override
                public void onGroup(Group g) {
                    fconv.setName(g.name);
                    fconv.setAvatar(g.avatarURL);
                }
            });
        } else {
            conv.setName(g.name);
        }
        conv.setAvatar(g.avatarURL);
    }

    void loadConversations() {
        conversations = new ArrayList<Conversation>();
        ConversationIterator iter = PeerMessageDB.getInstance().newConversationIterator();
        while (true) {
            Conversation conv = iter.next();
            if (conv == null) {
                break;
            }
            if (conv.message == null) {
                continue;
            }
            updatePeerConversationName(conv);
            updateConversationDetail(conv);
            conversations.add(conv);
        }

        iter = GroupMessageDB.getInstance().newConversationIterator();
        while (true) {
            Conversation conv = iter.next();
            if (conv == null) {
                break;
            }
            if (conv.message == null) {
                continue;
            }
            updateGroupConversationName(conv);
            updateNotificationDesc(conv);
            updateConversationDetail(conv);
            conversations.add(conv);
        }
        Comparator<Conversation> cmp = new Comparator<Conversation>() {
            public int compare(Conversation c1, Conversation c2) {
                if (c1.message.timestamp > c2.message.timestamp) {
                    return -1;
                } else if (c1.message.timestamp == c2.message.timestamp) {
                    return 0;
                } else {
                    return 1;
                }

            }
        };
        Collections.sort(conversations, cmp);
    }

    public static class User {
        public long uid;
        public String name;
        public String avatarURL;

        //name为nil时，界面显示identifier字段
        public String identifier;
    }

    public static class Group {
        public long gid;
        public String name;
        public String avatarURL;

        //name为nil时，界面显示identifier字段
        public String identifier;
    }

    protected User getUser(long uid) {
        User u = new User();
        u.uid = uid;
        u.name = null;
        u.avatarURL = "";
        u.identifier = String.format("%d", uid);
        return u;
    }

    public interface GetUserCallback {
        void onUser(User u);
    }

    public interface GetGroupCallback {
        void onGroup(Group g);
    }

    protected void asyncGetUser(long uid, GetUserCallback cb) {

    }

    protected void asyncGetGroup(long gid, GetGroupCallback cb) {

    }

    protected Group getGroup(long gid) {
        Group g = new Group();
        g.gid = gid;
        g.name = null;
        g.avatarURL = "";
        g.identifier = String.format("%d", gid);
        return g;
    }

    protected void onPeerClick(long uid) {

    }

    protected void onGroupClick(long gid) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position,
                            long id) {
        Conversation conv = conversations.get(position);
        Log.i(TAG, "conv:" + conv.getName());

        if (conv.type == Conversation.CONVERSATION_PEER) {
            onPeerClick(conv.cid);
        } else {
            onGroupClick(conv.cid);
        }
    }

    public void onConnectState(IMService.ConnectState state) {

    }

    public void onLoginPoint(LoginPoint lp) {

    }

    public void onPeerInputting(long uid) {

    }

    public void onPeerMessage(IMMessage msg) {
        Log.i(TAG, "on peer message");
        IMessage imsg = new IMessage();
        imsg.timestamp = now();
        imsg.msgLocalID = msg.msgLocalID;
        imsg.sender = msg.sender;
        imsg.receiver = msg.receiver;
        imsg.setContent(msg.content);

        long cid = 0;
        if (msg.sender == this.currentUID) {
            cid = msg.receiver;
        } else {
            cid = msg.sender;
        }

        int pos = findConversationPosition(cid, Conversation.CONVERSATION_PEER);
        Conversation conversation = null;
        if (pos == -1) {
            conversation = newPeerConversation(cid);
        } else {
            conversation = conversations.get(pos);
        }

        conversation.message = imsg;
        updateConversationDetail(conversation);

        if (pos == -1) {
            conversations.add(0, conversation);
            adapter.notifyDataSetChanged();
        } else if (pos > 0) {
            conversations.remove(pos);
            conversations.add(0, conversation);
            adapter.notifyDataSetChanged();
        } else {
            //pos == 0
        }
    }

    public Conversation findConversation(long cid, int type) {
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = conversations.get(i);
            if (conv.cid == cid && conv.type == type) {
                return conv;
            }
        }
        return null;
    }

    public int findConversationPosition(long cid, int type) {
        for (int i = 0; i < conversations.size(); i++) {
            Conversation conv = conversations.get(i);
            if (conv.cid == cid && conv.type == type) {
                return i;
            }
        }
        return -1;
    }

    public Conversation newPeerConversation(long cid) {
        Conversation conversation = new Conversation();
        conversation.type = Conversation.CONVERSATION_PEER;
        conversation.cid = cid;

        updatePeerConversationName(conversation);
        return conversation;
    }

    public Conversation newGroupConversation(long cid) {
        Conversation conversation = new Conversation();
        conversation.type = Conversation.CONVERSATION_GROUP;
        conversation.cid = cid;
        updateGroupConversationName(conversation);
        return conversation;
    }

    public static int now() {
        Date date = new Date();
        long t = date.getTime();
        return (int)(t/1000);
    }

    public void onPeerMessageACK(int msgLocalID, long uid) {
        Log.i(TAG, "message ack on main");
    }

    public void onPeerMessageFailure(int msgLocalID, long uid) {
    }

    public void onGroupMessage(IMMessage msg) {
        Log.i(TAG, "on group message");
        IMessage imsg = new IMessage();
        imsg.timestamp = msg.timestamp;
        imsg.msgLocalID = msg.msgLocalID;
        imsg.sender = msg.sender;
        imsg.receiver = msg.receiver;
        imsg.setContent(msg.content);

        int pos = findConversationPosition(msg.receiver, Conversation.CONVERSATION_GROUP);
        Conversation conversation = null;
        if (pos == -1) {
            conversation = newGroupConversation(msg.receiver);
        } else {
            conversation = conversations.get(pos);
        }

        conversation.message = imsg;
        updateConversationDetail(conversation);

        if (pos == -1) {
            conversations.add(0, conversation);
            adapter.notifyDataSetChanged();
        } else if (pos > 0) {
            conversations.remove(pos);
            conversations.add(0, conversation);
            adapter.notifyDataSetChanged();
        } else {
            //pos == 0
        }
    }

    public void onGroupMessageACK(int msgLocalID, long uid) {

    }
    public void onGroupMessageFailure(int msgLocalID, long uid) {

    }

    public void onGroupNotification(String text) {
        GroupNotification groupNotification = IMessage.newGroupNotification(text);
        IMessage imsg = new IMessage();
        imsg.sender = 0;
        imsg.receiver = groupNotification.groupID;
        imsg.timestamp = now();
        imsg.setContent(groupNotification);
        int pos = findConversationPosition(groupNotification.groupID, Conversation.CONVERSATION_GROUP);
        Conversation conv = null;
        if (pos == -1) {
            conv = newGroupConversation(groupNotification.groupID);
        } else {
            conv = conversations.get(pos);
        }
        conv.message = imsg;
        updateNotificationDesc(conv);
        updateConversationDetail(conv);
        if (pos == -1) {
            conversations.add(0, conv);
            adapter.notifyDataSetChanged();
        } else if (pos > 0) {
            //swap with 0
            conversations.remove(pos);
            conversations.add(0, conv);
            adapter.notifyDataSetChanged();
        } else {
            //pos == 0
        }
    }

    private void updateNotificationDesc(Conversation conv) {
        final IMessage imsg = conv.message;
        if (imsg == null || imsg.content.getType() != IMessage.MessageType.MESSAGE_GROUP_NOTIFICATION) {
            return;
        }
        long currentUID = this.currentUID;
        GroupNotification notification = (GroupNotification)imsg.content;
        if (notification.type == GroupNotification.NOTIFICATION_GROUP_CREATED) {
            if (notification.master == currentUID) {
                notification.description = String.format("您创建了\"%s\"群组", notification.groupName);
            } else {
                notification.description = String.format("您加入了\"%s\"群组", notification.groupName);
            }
        } else if (notification.type == GroupNotification.NOTIFICATION_GROUP_DISBAND) {
            notification.description = "群组已解散";
        } else if (notification.type == GroupNotification.NOTIFICATION_GROUP_MEMBER_ADDED) {
            User u = getUser(notification.member);
            if (TextUtils.isEmpty(u.name)) {
                notification.description = String.format("\"%s\"加入群", u.identifier);
                final GroupNotification fnotification = notification;
                final Conversation fconv = conv;
                asyncGetUser(notification.member, new GetUserCallback() {
                    @Override
                    public void onUser(User u) {
                        fnotification.description = String.format("\"%s\"加入群", u.name);
                        if (fconv.message == imsg) {
                            fconv.setDetail(fnotification.description);
                        }
                    }
                });
            } else {
                notification.description = String.format("\"%s\"加入群", u.name);
            }
        } else if (notification.type == GroupNotification.NOTIFICATION_GROUP_MEMBER_LEAVED) {
            User u = getUser(notification.member);
            if (TextUtils.isEmpty(u.name)) {
                notification.description = String.format("\"%s\"离开群", u.identifier);
                final GroupNotification fnotification = notification;
                final Conversation fconv = conv;
                asyncGetUser(notification.member, new GetUserCallback() {
                    @Override
                    public void onUser(User u) {
                        fnotification.description = String.format("\"%s\"离开群", u.name);
                        if (fconv.message == imsg) {
                            fconv.setDetail(fnotification.description);
                        }
                    }
                });
            } else {
                notification.description = String.format("\"%s\"离开群", u.name);
            }
        }
    }

    @Override
    public void onNotification(Notification notification) {
        if (notification.name.equals(PeerMessageActivity.SEND_MESSAGE_NAME)) {
            IMessage imsg = (IMessage) notification.obj;

            int pos = findConversationPosition(imsg.receiver, Conversation.CONVERSATION_PEER);
            Conversation conversation = null;
            if (pos == -1) {
                conversation = newPeerConversation(imsg.receiver);
            } else {
                conversation = conversations.get(pos);
            }

            conversation.message = imsg;
            updateConversationDetail(conversation);

            if (pos == -1) {
                conversations.add(0, conversation);
                adapter.notifyDataSetChanged();
            } else if (pos > 0){
                conversations.remove(pos);
                conversations.add(0, conversation);
                adapter.notifyDataSetChanged();
            } else {
                //pos == 0
            }

        } else if (notification.name.equals(PeerMessageActivity.CLEAR_MESSAGES)) {
            Long peerUID = (Long)notification.obj;
            Conversation conversation = findConversation(peerUID, Conversation.CONVERSATION_PEER);
            if (conversation != null) {
                conversations.remove(conversation);
                adapter.notifyDataSetChanged();
            }
        } else if (notification.name.equals(GroupMessageActivity.SEND_MESSAGE_NAME)) {
            IMessage imsg = (IMessage) notification.obj;
            int pos = findConversationPosition(imsg.receiver, Conversation.CONVERSATION_GROUP);
            Conversation conversation = null;
            if (pos == -1) {
                conversation = newGroupConversation(imsg.receiver);
            } else {
                conversation = conversations.get(pos);
            }

            conversation.message = imsg;
            updateConversationDetail(conversation);

            if (pos == -1) {
                conversations.add(0, conversation);
                adapter.notifyDataSetChanged();
            } else if (pos > 0){
                conversations.remove(pos);
                conversations.add(0, conversation);
                adapter.notifyDataSetChanged();
            } else {
                //pos == 0
            }

        }  else if (notification.name.equals(GroupMessageActivity.CLEAR_MESSAGES)) {
            Long groupID = (Long)notification.obj;
            Conversation conversation = findConversation(groupID, Conversation.CONVERSATION_GROUP);
            if (conversation != null) {
                conversations.remove(conversation);
                adapter.notifyDataSetChanged();
            }
        }
    }

    public boolean canBack() {
        return false;
    }

}
