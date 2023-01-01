/*

for information about monome devices:
monome.org

written by:
raja das, ezra buchla, dan derks

*/

MonoM {

    classvar seroscnet, discovery, <>rows, <>columns, <>portlst, quadDirty, ledQuads, redrawTimer;
	var <>prefix, <>rot, <>dvcnum, oscout;

    *initClass {

        var sz, rw, cl;

        portlst = List.new(0);
        rows = List.new(0);
        columns = List.new(0);
        seroscnet = NetAddr.new("localhost", 12002);
        seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);

        StartUp.add {

            discovery = OSCdef.newMatching(\monomediscover,
                {|msg, time, addr, recvPort|

                    sz = msg[2].asString.replace("monome","").replace("40h",64).asInteger;
                    rw = case
                    {sz == 64} {8}
                    {sz == 128}{8}
                    {sz == 256}{16};
                    cl = case
                    {sz == 64} {8}
                    {sz == 128}{16}
                    {sz == 256}{16};
                    rows.add(rw);
                    columns.add(cl);
                    portlst.add(msg[3]);
                    ("Device connected on port:"++msg[3]).postln;
                    msg[1].postln;
                    msg[2].postln;

            }, '/serialosc/device', seroscnet);

        }

    }

    *new { arg prefix, rot;
        ^ super.new.init(prefix, rot);
    }

    init { arg prefix_, rot_;
        prefix = prefix_;
        rot = rot_;
		quadDirty = Array.fill(8,{0});
		ledQuads = Array.fill(8,{Array.fill(64,{0})});
		redrawTimer = Routine({
			var interval = 1/60, offsets = [[0,0],[8,0],[0,8],[8,8]];
			loop {
				for (0, 3, {
					arg i;
					if(quadDirty[i] != 0,
						{
							oscout.sendMsg(
								prefix++"/grid/led/level/map",
								offsets[i][0],
								offsets[i][1],
								*ledQuads[i]
							);
							quadDirty[i] = 0;
						}
					);
					interval.yield;
				});
			}
		});

		redrawTimer.play();

    }

    deviceList {
        portlst.clear; rows.clear; columns.clear;
        seroscnet.sendMsg("/serialosc/list", "127.0.0.1", NetAddr.localAddr.port);
    }

    printOn { arg stream;
        stream << "Ports:" << portlst;
    }

    useDevice { arg devicenum;
        dvcnum = devicenum;
        oscout = NetAddr.new("localhost", portlst[devicenum].value);
        Post << "Using device on port#" << portlst[devicenum].value << Char.nl;

		oscout.sendMsg(prefix++"/grid/led/all", 0);

        oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
        oscout.sendMsg("/sys/prefix", prefix);
        oscout.sendMsg("/sys/rotation", rot);
    }

    usePort { arg portnum;
        dvcnum = portlst.indexOf(portnum);
        oscout = NetAddr.new("localhost", portnum);
        Post << "Using device#" << dvcnum << Char.nl;

        oscout.sendMsg("/sys/port", NetAddr.localAddr.port);
        oscout.sendMsg("/sys/prefix", prefix);
        oscout.sendMsg("/sys/rotation", rot);
    }

    prt {
        ^portlst[dvcnum];
    }

    rws {
        ^rows[dvcnum];
    }

    cls {
        ^columns[dvcnum];
    }

    // See here: http://monome.org/docs/tech:osc
    // if you need further explanation of the LED methods below
    ledset	{ arg col, row, state;
        if ((state == 0) or: (state == 1)) {
            oscout.sendMsg(prefix++"/grid/led/set", col, row, state);
        } {
            "invalid argument (state must be 0 or 1).".warn;
        };
    }

    ledall 	{ arg state;
        if ((state == 1) or: (state == 0)) {
            oscout.sendMsg(prefix++"/grid/led/all", state);
        } {
            "invalid argument (state must be 0 or 1)".warn;
        };
    }

    ledmap	{ arg xOffset, yOffset, levArray;
        oscout.sendMsg(prefix++"/grid/led/map", xOffset, yOffset, *levArray);
    }

    ledrow	{ arg xOffset, y, bit1, bit2;
        oscout.sendMsg(prefix++"/grid/led/row", xOffset, y, bit1, bit2);
    }

    ledcol	{ arg x, yOffset, bit1, bit2;
        oscout.sendMsg(prefix++"/grid/led/col", x, yOffset, bit1, bit2);
    }

    intensity	{ arg globalIntensity;
        oscout.sendMsg(prefix++"/grid/led/intensity", globalIntensity);
    }

    levset	{ arg col, row, lev;
		var x = col, y = row, offset;
		case
		{(x < 8) && (y < 8)} {
			offset = (8*y)+x;
			ledQuads[0][offset] = lev;
			quadDirty[0] = 1;
		}
		{(x > 7) && (x < 16) && (y < 8)} {
			offset = (8*y)+(x-8);
			ledQuads[1][offset] = lev;
			quadDirty[1] = 1;
		}
		{(x < 8) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+x;
			ledQuads[2][offset] = lev;
			quadDirty[2] = 1;
		}
		{(x > 7) && (x < 16) && (y > 7) && (y < 16)} {
			offset = (8*(y-8))+(x-8);
			ledQuads[3][offset] = lev;
			quadDirty[3] = 1;
		}
    }

    levall	{ arg lev;
        oscout.sendMsg(prefix++"/grid/led/level/all", lev);
    }

    levmap	{ arg xOffset, yOffset, levArray;
        oscout.sendMsg(prefix++"/grid/led/level/map", xOffset, yOffset, *levArray);
    }

    levrow	{ arg xOffset, y, levArray;
        oscout.sendMsg(prefix++"/grid/led/level/row", xOffset, y, *levArray);
    }

    levcol	{ arg x, yOffset, levArray;
        oscout.sendMsg(prefix++"/grid/led/level/col", x, yOffset, *levArray);
    }

    tiltnoff { arg sens, state;
        oscout.sendMsg(prefix++"/tilt/set", sens, state);
    }

    darkness {
        this.ledall(0);
        discovery.free;
        oscout.disconnect;
        seroscnet.disconnect;
    }

}