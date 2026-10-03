import unittest
from inventory import collect, split_args, calls, class_type
from features import verdict

def site(status='RESOLVED', kind='M', owner='MessagesController', member='isChatNoForwards', source='features/other/RemovesContentSaving.java', signature=''):
    return [status,kind,owner,member,source,'',signature,'resolved']
class AuditTests(unittest.TestCase):
    def test_parser(self):
        self.assertEqual(split_args('a(x, y), new String[]{"a,b", "c"}, z'), ['a(x, y)', 'new String[]{"a,b", "c"}', 'z'])
        self.assertEqual(list(calls('f("x)", g(a,b), new Object[]{x,y})', 'f')), [['"x)"','g(a,b)','new Object[]{x,y}']])
        self.assertEqual(class_type('int[].class', {}), 'int[]')
    def test_unresolved_new_feature_fails(self):
        result = verdict([site('UNRESOLVED', source='features/new/Future.java')], 'org.telegram.messenger')
        self.assertEqual(result[0][0], 'FAIL')
    def test_client_scope(self):
        old = site('UNRESOLVED', source='settings/hook/SettingsHook.java')
        self.assertEqual(verdict([old], 'org.telegram.messenger')[0][0], 'INACTIVE')
        self.assertEqual(verdict([old], 'org.telegram.messenger.web')[0][0], 'FAIL')
        photo = site('UNRESOLVED', owner='PhotoViewer', member='setParentActivity', source='virtuals/ui/PhotoViewer.java')
        self.assertEqual(verdict([photo], 'org.telegram.messenger')[0][0], 'INACTIVE')
        photo[3] = 'newMethod'
        self.assertEqual(verdict([photo], 'org.telegram.messenger')[0][0], 'FAIL')
    def test_no_empty_success(self):
        with self.assertRaises(ValueError): verdict([], 'org.telegram.messenger')
        with self.assertRaises(ValueError): verdict([['RESOLVED']], 'org.telegram.messenger')
    def test_privacy_hooks_and_arrays_collected(self):
        sites, gaps = collect()
        self.assertIn(('H','org.telegram.ui.ChatActivity','db','features/media/PreventMedia.java','org.telegram.messenger.MessageObject,boolean'), sites)
        self.assertIn(('H','org.telegram.ui.ChatActivity','N4','features/media/PreventMedia.java','org.telegram.ui.ChatActivity,org.telegram.messenger.MessageObject'), sites)
        self.assertIn(('H','MessagesController','storiesEnabled','features/stories/DisableStories.java','-'), sites)
        self.assertIn(('C','org.telegram.tgnet.TLRPC$TL_contacts_getSponsoredPeers','','features/ui/AdBlock.java',''), sites)
        self.assertIn(('F','TLRPC$TL_messages_sendMessage','peer','features/ghostMode/HideSeen.java',''), sites)
        self.assertGreater(len(sites), 300)
if __name__ == '__main__': unittest.main()
