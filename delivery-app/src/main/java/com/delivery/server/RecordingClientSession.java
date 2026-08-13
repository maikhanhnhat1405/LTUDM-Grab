package com.delivery.server;

import com.delivery.common.Message;
import com.delivery.server.model.Role;

import java.io.DataInputStream;

/**
 * Wrap ClientSession that: chuyen tiep moi loi goi den session goc,
 * dong thoi cho RecordingSession co hoi ghi lai response dau tien.
 *
 * Cach lam nay xam pham it nhat vao code cu: cac service khong biet ho
 * dang duoc theo doi, cu dung ClientSession nhu binh thuong.
 */
class RecordingClientSession extends ClientSession {
    private final ClientSession delegate;
    private final RecordingSession recorder;

    private static ClientSession dummy() {
        // Constructor cua ClientSession can Socket + IOException. Ta khong the goi.
        throw new UnsupportedOperationException();
    }

    // Cach vong qua: dung constructor bi mat cua ClientSession
    // -> khong lam duoc, boi vi Java khong cho phep goi cha khac. Nen ta lam
    //    theo huong nguoc lai: KHONG ke thua, chi delegate.
    //    Nhung cac service can kieu ClientSession. Vay reflection... hoac
    //    doi ClientSession thanh interface? Cach nhe nhat: tao constructor
    //    protected khong doi so trong ClientSession, chi dung noi bo package.
    RecordingClientSession(ClientSession delegate, RecordingSession recorder) {
        super();   // can constructor protected khong doi so trong ClientSession
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override public void send(Message m)         { recorder.send(m); }   // recorder tu goi tiep delegate
    @Override public boolean isAuthenticated()    { return delegate.isAuthenticated(); }
    @Override public long userId()                { return delegate.userId(); }
    @Override public String fullName()            { return delegate.fullName(); }
    @Override public Role role()                  { return delegate.role(); }
    @Override public long udpToken()              { return delegate.udpToken(); }
    @Override public DataInputStream in()         { return delegate.in(); }
    @Override public String describe()            { return delegate.describe(); }
    @Override public void close()                 { delegate.close(); }
}
